package vn.sgu.narration.messaging;

import tools.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.config.RabbitConfiguration;
import vn.sgu.narration.service.JobEventProcessor;

import java.io.IOException;

@Component
public class NarrationResultListener {
    private static final Logger log = LoggerFactory.getLogger(NarrationResultListener.class);
    private final ObjectMapper objectMapper;
    private final JobEventProcessor processor;
    private final RetryTemplate retryTemplate;

    public NarrationResultListener(ObjectMapper objectMapper, JobEventProcessor processor,
                                   RetryTemplate databaseRetryTemplate) {
        this.objectMapper = objectMapper;
        this.processor = processor;
        this.retryTemplate = databaseRetryTemplate;
    }

    @RabbitListener(queues = RabbitConfiguration.TRANSLATION_COMPLETED_QUEUE, ackMode = "MANUAL")
    public void translationCompleted(Message message, Channel channel) throws IOException {
        consume(message, channel, "translation.completed");
    }

    @RabbitListener(queues = RabbitConfiguration.RESULTS_QUEUE, ackMode = "MANUAL")
    public void results(Message message, Channel channel) throws IOException {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        consume(message, channel, routingKey);
    }

    private void consume(Message message, Channel channel, String routingKey) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        Object correlationHeader = message.getMessageProperties().getHeaders().get("correlationId");
        String correlationId = correlationHeader == null ? "unknown" : correlationHeader.toString();
        MDC.put("correlationId", correlationId);
        try {
            retryTemplate.execute(context -> {
                dispatch(routingKey, message.getBody());
                return null;
            });
            channel.basicAck(tag, false);
        } catch (AppException ex) {
            log.warn("Rejecting malformed event routingKey={} code={} correlationId={}",
                    routingKey, ex.errorCode(), correlationId);
            channel.basicNack(tag, false, false);
        } catch (Exception ex) {
            log.error("Event processing failed after bounded retries; routingKey={} correlationId={}",
                    routingKey, correlationId, ex);
            channel.basicNack(tag, false, false);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void dispatch(String routingKey, byte[] body) throws Exception {
        switch (routingKey) {
            case "translation.completed" -> processor.translationCompleted(
                    objectMapper.readValue(body, Contracts.TranslationCompleted.class));
            case "translation.failed" -> processor.translationFailed(
                    objectMapper.readValue(body, Contracts.TranslationFailed.class));
            case "tts.completed" -> processor.ttsCompleted(
                    objectMapper.readValue(body, Contracts.TtsCompleted.class));
            case "tts.failed" -> processor.ttsFailed(
                    objectMapper.readValue(body, Contracts.TtsFailed.class));
            default -> throw new AppException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_EVENT", "Routing key không được hỗ trợ");
        }
    }
}
