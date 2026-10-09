package vn.sgu.narration.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.service.JobEventProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NarrationResultListenerTest {
    private JobEventProcessor processor;
    private NarrationResultListener listener;
    private Channel channel;

    @BeforeEach
    void setUp() {
        processor = mock(JobEventProcessor.class);
        channel = mock(Channel.class);
        RetryTemplate retries = new RetryTemplate();
        retries.setRetryPolicy(new SimpleRetryPolicy(2, Map.of(
                AppException.class, false,
                JacksonException.class, false,
                Exception.class, true)));
        retries.setBackOffPolicy(new NoBackOffPolicy());
        listener = new NarrationResultListener(new ObjectMapper(), processor, retries);
    }

    @Test
    void validTranslationCompletedEventIsProcessedAndAcknowledged() throws Exception {
        Message message = message("translation.completed", """
                {"jobId":"j-1","targetId":"t-en","translationId":"tr-1","lang":"en","voiceId":"en-voice"}
                """);

        listener.translationCompleted(message, channel);

        verify(processor).translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-1", "en", "en-voice"));
        verify(channel).basicAck(42L, false);
    }

    @Test
    void supportedResultEventsAreRoutedToTheirProcessors() throws Exception {
        listener.results(message("translation.failed", """
                {"jobId":"j-1","targetId":"t-en","lang":"en","errorCode":"TIMEOUT"}
                """), channel);
        listener.results(message("tts.completed", """
                {"jobId":"j-1","targetId":"t-en","translationId":"tr-1","audioId":"au-1","lang":"en"}
                """), channel);
        listener.results(message("tts.failed", """
                {"jobId":"j-1","targetId":"t-en","lang":"en","errorCode":"PROVIDER_ERROR"}
                """), channel);

        verify(processor).translationFailed(new Contracts.TranslationFailed("j-1", "t-en", "en", "TIMEOUT"));
        verify(processor).ttsCompleted(new Contracts.TtsCompleted("j-1", "t-en", "tr-1", "au-1", "en"));
        verify(processor).ttsFailed(new Contracts.TtsFailed("j-1", "t-en", "en", "PROVIDER_ERROR"));
        verify(channel, times(3)).basicAck(42L, false);
    }

    @Test
    void unsupportedRouteAndMalformedJsonAreRejectedWithoutRequeue() throws Exception {
        listener.results(message("unknown.event", "{}"), channel);
        listener.translationCompleted(message("translation.completed", "{"), channel);

        verify(channel, times(2)).basicNack(42L, false, false);
        verifyNoInteractions(processor);
    }

    @Test
    void transientProcessingFailureIsRetriedThenRejected() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).when(processor)
                .ttsFailed(any(Contracts.TtsFailed.class));

        listener.results(message("tts.failed", """
                {"jobId":"j-1","targetId":"t-en","lang":"en","errorCode":"TIMEOUT"}
                """), channel);

        verify(processor, times(2)).ttsFailed(any(Contracts.TtsFailed.class));
        verify(channel).basicNack(42L, false, false);
    }

    private static Message message(String routingKey, String body) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(42L);
        properties.setReceivedRoutingKey(routingKey);
        properties.setHeader("correlationId", "corr-1");
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
