package vn.sgu.narration.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import tools.jackson.core.JacksonException;
import vn.sgu.narration.api.AppException;

import java.util.Map;

@Configuration
public class RabbitConfiguration {
    public static final String EVENTS_EXCHANGE = "narration.events";
    public static final String CANCEL_EXCHANGE = "narration.cancel";
    public static final String DLX = "narration.dlx";
    public static final String TRANSLATION_COMPLETED_QUEUE = "narration.q.translation-completed";
    public static final String RESULTS_QUEUE = "narration.q.results";
    public static final String REQUESTED_QUEUE = "translation.q.requested";
    public static final String TTS_COMPLETED_QUEUE = "tts.q.translation-completed";

    @Bean TopicExchange narrationEventsExchange() { return new TopicExchange(EVENTS_EXCHANGE, true, false); }
    @Bean FanoutExchange narrationCancelExchange() { return new FanoutExchange(CANCEL_EXCHANGE, true, false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DLX, true, false); }

    @Bean Queue translationCompletedQueue() {
        return QueueBuilder.durable(TRANSLATION_COMPLETED_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(TRANSLATION_COMPLETED_QUEUE + ".dlq").build();
    }

    @Bean Queue translationRequestedQueue() {
        return QueueBuilder.durable(REQUESTED_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(REQUESTED_QUEUE + ".dlq").build();
    }

    @Bean Queue ttsTranslationCompletedQueue() {
        return QueueBuilder.durable(TTS_COMPLETED_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(TTS_COMPLETED_QUEUE + ".dlq").build();
    }

    @Bean Queue narrationResultsQueue() {
        return QueueBuilder.durable(RESULTS_QUEUE)
                .deadLetterExchange(DLX).deadLetterRoutingKey(RESULTS_QUEUE + ".dlq").build();
    }

    @Bean Queue translationCompletedDlq() {
        return QueueBuilder.durable(TRANSLATION_COMPLETED_QUEUE + ".dlq")
                .ttl(86_400_000).build();
    }

    @Bean Queue translationRequestedDlq() {
        return QueueBuilder.durable(REQUESTED_QUEUE + ".dlq").ttl(86_400_000).build();
    }

    @Bean Queue ttsTranslationCompletedDlq() {
        return QueueBuilder.durable(TTS_COMPLETED_QUEUE + ".dlq").ttl(86_400_000).build();
    }

    @Bean Queue narrationResultsDlq() {
        return QueueBuilder.durable(RESULTS_QUEUE + ".dlq")
                .ttl(86_400_000).build();
    }

    @Bean Binding translationCompletedBinding(Queue translationCompletedQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(translationCompletedQueue).to(narrationEventsExchange).with("translation.completed");
    }

    @Bean Binding translationRequestedBinding(Queue translationRequestedQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(translationRequestedQueue).to(narrationEventsExchange).with("narration.requested");
    }

    @Bean Binding ttsTranslationCompletedBinding(Queue ttsTranslationCompletedQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(ttsTranslationCompletedQueue).to(narrationEventsExchange).with("translation.completed");
    }

    @Bean Binding resultsTranslationFailedBinding(Queue narrationResultsQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(narrationResultsQueue).to(narrationEventsExchange).with("translation.failed");
    }

    @Bean Binding resultsTtsCompletedBinding(Queue narrationResultsQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(narrationResultsQueue).to(narrationEventsExchange).with("tts.completed");
    }

    @Bean Binding resultsTtsFailedBinding(Queue narrationResultsQueue, TopicExchange narrationEventsExchange) {
        return BindingBuilder.bind(narrationResultsQueue).to(narrationEventsExchange).with("tts.failed");
    }

    @Bean Binding translationCompletedDlqBinding(Queue translationCompletedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(translationCompletedDlq).to(deadLetterExchange).with(TRANSLATION_COMPLETED_QUEUE + ".dlq");
    }

    @Bean Binding translationRequestedDlqBinding(Queue translationRequestedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(translationRequestedDlq).to(deadLetterExchange).with(REQUESTED_QUEUE + ".dlq");
    }

    @Bean Binding ttsTranslationCompletedDlqBinding(Queue ttsTranslationCompletedDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(ttsTranslationCompletedDlq).to(deadLetterExchange).with(TTS_COMPLETED_QUEUE + ".dlq");
    }

    @Bean Binding resultsDlqBinding(Queue narrationResultsDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(narrationResultsDlq).to(deadLetterExchange).with(RESULTS_QUEUE + ".dlq");
    }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean RetryTemplate databaseRetryTemplate() {
        RetryTemplate retry = new RetryTemplate();
        retry.setRetryPolicy(new SimpleRetryPolicy(4, Map.of(
                AppException.class, false,
                JacksonException.class, false,
                Exception.class, true)));
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(250);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(2_000);
        retry.setBackOffPolicy(backoff);
        return retry;
    }
}
