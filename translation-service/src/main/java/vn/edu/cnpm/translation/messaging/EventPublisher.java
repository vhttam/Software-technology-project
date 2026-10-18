package vn.edu.cnpm.translation.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Component
public class EventPublisher {
    private final RabbitTemplate rabbit; private final ObjectMapper mapper;
    public EventPublisher(RabbitTemplate rabbit,ObjectMapper mapper) { this.rabbit=rabbit;this.mapper=mapper; }
    public void publish(String routingKey,Object payload,String correlationId) {
        try {
            String eventId=UUID.randomUUID().toString();
            MessageProperties properties=new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader("eventId",eventId);
            properties.setHeader("correlationId",correlationId);
            properties.setHeader("schemaVersion","1.0");
            properties.setHeader("occurredAt",Instant.now().toString());
            Message message=MessageBuilder.withBody(mapper.writeValueAsBytes(payload)).andProperties(properties).build();
            CorrelationData confirm=new CorrelationData(eventId);
            rabbit.send("narration.events",routingKey,message,confirm);
            var result=confirm.getFuture().get(5,TimeUnit.SECONDS);
            if(!result.isAck()||confirm.getReturned()!=null) throw new IllegalStateException("Broker không xác nhận event "+routingKey);
        } catch(Exception ex) { throw new IllegalStateException("Không thể phát event "+routingKey,ex); }
    }
}
