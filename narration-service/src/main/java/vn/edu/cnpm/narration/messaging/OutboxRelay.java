package vn.edu.cnpm.narration.messaging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.narration.service.NarrationStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class OutboxRelay {
    private final JdbcTemplate jdbc;private final RabbitTemplate rabbit;private final ObjectMapper mapper;
    private final NarrationStore store;private final CallbackClient callback;
    public OutboxRelay(JdbcTemplate jdbc,RabbitTemplate rabbit,ObjectMapper mapper,NarrationStore store,CallbackClient callback){
        this.jdbc=jdbc;this.rabbit=rabbit;this.mapper=mapper;this.store=store;this.callback=callback;
    }
    @Scheduled(fixedDelay=1000)
    public void publishPending(){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT event_id,exchange_name,routing_key,payload,correlation_id,occurred_at FROM outbox_events WHERE published=0 ORDER BY occurred_at");
        for(Map<String,Object> row:rows)publish(row);
    }
    private void publish(Map<String,Object> row){
        String id=String.valueOf(row.get("event_id")),exchange=String.valueOf(row.get("exchange_name"));
        try{
            JsonNode payload=mapper.readTree(String.valueOf(row.get("payload")));
            if("narration.requested".equals(row.get("routing_key"))){
                Map<String,Object> job=store.markRequested(payload.path("targetId").asText());
                if(job==null){jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?",id);return;}
            }
            MessageProperties props=new MessageProperties();props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);props.setHeader("eventId",id);
            props.setHeader("correlationId",row.get("correlation_id"));props.setHeader("schemaVersion","1.0");
            props.setHeader("occurredAt",row.get("occurred_at"));
            Message message=MessageBuilder.withBody(String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8)).andProperties(props).build();
            CorrelationData confirm=new CorrelationData(id);
            rabbit.send(exchange,String.valueOf(row.get("routing_key")),message,confirm);
            var result=confirm.getFuture().get(5,TimeUnit.SECONDS);
            if(!result.isAck()||confirm.getReturned()!=null)throw new IllegalStateException("Broker did not confirm event");
            jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?",id);
            if("narration.requested".equals(row.get("routing_key")))callback.notify(payload.path("jobId").asText(),String.valueOf(row.get("correlation_id")),store.getJob(payload.path("jobId").asText()));
        }catch(Exception ex){
            // Bản ghi chỉ được đánh dấu sau publisher confirm nên broker lỗi không làm mất event.
        }
    }
}
