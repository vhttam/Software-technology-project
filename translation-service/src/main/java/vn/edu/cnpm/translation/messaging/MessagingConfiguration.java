package vn.edu.cnpm.translation.messaging;
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
    @Bean Queue translationRequestedQueue() {
        return QueueBuilder.durable("translation.q.requested")
                .withArgument("x-dead-letter-exchange","narration.dlx")
                .withArgument("x-dead-letter-routing-key","translation.q.requested.dlq").build();
    }
    @Bean Queue translationRequestedDlq() { return QueueBuilder.durable("translation.q.requested.dlq").withArgument("x-message-ttl",86_400_000).build(); }
    @Bean Binding translationRequestedDlqBinding() { return BindingBuilder.bind(translationRequestedDlq()).to(deadLetterExchange()).with("translation.q.requested.dlq"); }
    @Bean Binding translationRequestedBinding() { return BindingBuilder.bind(translationRequestedQueue()).to(eventsExchange()).with("narration.requested"); }
    @Bean Queue cancelQueue() { return new AnonymousQueue(); }
    @Bean Binding cancelBinding() { return BindingBuilder.bind(cancelQueue()).to(cancelExchange()); }
}
