package vn.edu.cnpm.tts.messaging;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class MessagingConfiguration {
    @Bean TopicExchange eventsExchange() { return new TopicExchange("narration.events",true,false); }
    @Bean FanoutExchange cancelExchange() { return new FanoutExchange("narration.cancel",true,false); }
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange("narration.dlx",true,false); }
    @Bean Queue ttsTranslationCompletedQueue() {
        return QueueBuilder.durable("tts.q.translation-completed")
                .withArgument("x-dead-letter-exchange","narration.dlx")
                .withArgument("x-dead-letter-routing-key","tts.q.translation-completed.dlq").build();
    }
    @Bean Queue ttsTranslationCompletedDlq() { return QueueBuilder.durable("tts.q.translation-completed.dlq").withArgument("x-message-ttl",86_400_000).build(); }
    @Bean Binding ttsDlqBinding() { return BindingBuilder.bind(ttsTranslationCompletedDlq()).to(deadLetterExchange()).with("tts.q.translation-completed.dlq"); }
    @Bean Binding ttsInputBinding() { return BindingBuilder.bind(ttsTranslationCompletedQueue()).to(eventsExchange()).with("translation.completed"); }
    @Bean Queue cancelQueue() { return new AnonymousQueue(); }
    @Bean Binding cancelBinding() { return BindingBuilder.bind(cancelQueue()).to(cancelExchange()); }
}
