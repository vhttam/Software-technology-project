package vn.edu.cnpm.content.messaging;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class ContentMessagingConfiguration {
    @Bean TopicExchange narrationEventsExchange() { return new TopicExchange("narration.events",true,false); }
}
