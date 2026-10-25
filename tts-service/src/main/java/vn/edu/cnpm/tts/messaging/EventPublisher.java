package vn.edu.cnpm.tts.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
@Component
public class EventPublisher {
    private final RabbitTemplate rabbit;private final ObjectMapper mapper;
    public EventPublisher(RabbitTemplate rabbit,ObjectMapper mapper){this.rabbit=rabbit;this.mapper=mapper;}
    public void publish(String route,Object payload,String correlationId) {
        try {
            String id=UUID.randomUUID().toString();MessageProperties properties=new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setHeader("eventId",id);properties.setHeader("correlationId",correlationId);
            properties.setHeader("schemaVersion","1.0");properties.setHeader("occurredAt",Instant.now().toString());
            Message message=MessageBuilder.withBody(mapper.writeValueAsBytes(payload)).andProperties(properties).build();
            CorrelationData data=new CorrelationData(id);rabbit.send("narration.events",route,message,data);
            var confirm=data.getFuture().get(5,TimeUnit.SECONDS);
            if(!confirm.isAck()||data.getReturned()!=null)throw new IllegalStateException("Broker không xác nhận event "+route);
        } catch(Exception ex){throw new IllegalStateException("Không thể phát event "+route,ex);}
    }
}
