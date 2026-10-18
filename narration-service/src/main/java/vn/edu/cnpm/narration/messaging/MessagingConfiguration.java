package vn.edu.cnpm.narration.messaging;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class MessagingConfiguration {
    @Bean TopicExchange eventsExchange(){return new TopicExchange("narration.events",true,false);}
    @Bean FanoutExchange cancelExchange(){return new FanoutExchange("narration.cancel",true,false);}
    @Bean DirectExchange deadLetterExchange(){return new DirectExchange("narration.dlx",true,false);}
    @Bean Queue narrationTranslationCompletedQueue(){return QueueBuilder.durable("narration.q.translation-completed")
            .withArgument("x-dead-letter-exchange","narration.dlx").withArgument("x-dead-letter-routing-key","narration.q.translation-completed.dlq").build();}
    @Bean Queue narrationTranslationCompletedDlq(){return QueueBuilder.durable("narration.q.translation-completed.dlq").withArgument("x-message-ttl",86_400_000).build();}
    @Bean Binding narrationTranslationDlqBinding(){return BindingBuilder.bind(narrationTranslationCompletedDlq()).to(deadLetterExchange()).with("narration.q.translation-completed.dlq");}
    @Bean Binding narrationTranslationCompletedBinding(){return BindingBuilder.bind(narrationTranslationCompletedQueue()).to(eventsExchange()).with("translation.completed");}
    @Bean Queue narrationResultsQueue(){return QueueBuilder.durable("narration.q.results")
            .withArgument("x-dead-letter-exchange","narration.dlx").withArgument("x-dead-letter-routing-key","narration.q.results.dlq").build();}
    @Bean Queue narrationResultsDlq(){return QueueBuilder.durable("narration.q.results.dlq").withArgument("x-message-ttl",86_400_000).build();}
    @Bean Binding narrationResultsDlqBinding(){return BindingBuilder.bind(narrationResultsDlq()).to(deadLetterExchange()).with("narration.q.results.dlq");}
    @Bean Binding resultTranslationFailedBinding(){return BindingBuilder.bind(narrationResultsQueue()).to(eventsExchange()).with("translation.failed");}
    @Bean Binding resultTtsCompletedBinding(){return BindingBuilder.bind(narrationResultsQueue()).to(eventsExchange()).with("tts.completed");}
    @Bean Binding resultTtsFailedBinding(){return BindingBuilder.bind(narrationResultsQueue()).to(eventsExchange()).with("tts.failed");}
}
