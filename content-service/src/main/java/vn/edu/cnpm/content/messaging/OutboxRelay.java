package vn.edu.cnpm.content.messaging;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class OutboxRelay {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    public OutboxRelay(JdbcTemplate jdbc,RabbitTemplate rabbit) { this.jdbc=jdbc;this.rabbit=rabbit; }
    @Scheduled(fixedDelay=1000)
    public void publishPending() {
        List<Map<String,Object>> pending=jdbc.queryForList(
                "SELECT event_id,routing_key,payload,correlation_id,occurred_at FROM outbox_events WHERE published=0 ORDER BY occurred_at");
        for(Map<String,Object> row:pending) publish(row);
    }
    private void publish(Map<String,Object> row) {
        String id=String.valueOf(row.get("event_id"));
        MessageProperties properties=new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setHeader("eventId",id);
        properties.setHeader("correlationId",row.get("correlation_id"));
        properties.setHeader("schemaVersion","1.0");
        properties.setHeader("occurredAt",row.get("occurred_at"));
        Message message=MessageBuilder.withBody(String.valueOf(row.get("payload")).getBytes(StandardCharsets.UTF_8))
                .andProperties(properties).build();
        CorrelationData confirm=new CorrelationData(id);
        try {
            rabbit.send("narration.events",String.valueOf(row.get("routing_key")),message,confirm);
            var result=confirm.getFuture().get(5,TimeUnit.SECONDS);
            if(result.isAck() && confirm.getReturned()==null) jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?",id);
        } catch(Exception ignored) {
            // Giữ bản ghi outbox chưa gửi để lần chạy sau thử lại sau khi broker phục hồi.
        }
    }
}
