package vn.sgu.narration.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sgu.narration.domain.OutboxEntity;
import vn.sgu.narration.repository.OutboxRepository;
import vn.sgu.narration.service.OutboxStateService;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository repository;
    private final OutboxStateService state;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxRepository repository, OutboxStateService state, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.state = state;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "PT1S")
    public void publishPending() {
        for (OutboxEntity event : repository.findTop100BySentAtIsNullOrderByCreatedAtAsc()) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEntity event) {
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        try {
            rabbitTemplate.convertAndSend(event.getExchangeName(), event.getRoutingKey(), event.getPayloadJson(), message -> {
                var properties = message.getMessageProperties();
                properties.setContentType("application/json");
                properties.setContentEncoding(StandardCharsets.UTF_8.name());
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(event.getEventId().toString());
                properties.setHeader("eventId", event.getEventId().toString());
                properties.setHeader("correlationId", event.getCorrelationId());
                properties.setHeader("schemaVersion", "1");
                properties.setHeader("occurredAt", event.getOccurredAt().toString());
                return message;
            }, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.ack()) throw new IllegalStateException("Broker nack: " + confirm.reason());
            if (correlation.getReturned() != null) throw new IllegalStateException("Message returned: " + correlation.getReturned().getReplyText());
            state.sent(event.getId());
        } catch (Exception ex) {
            state.failed(event.getId(), ex.getMessage());
            log.warn("Outbox publish failed eventId={} attempt={}", event.getEventId(), event.getAttempts() + 1, ex);
        }
    }
}
