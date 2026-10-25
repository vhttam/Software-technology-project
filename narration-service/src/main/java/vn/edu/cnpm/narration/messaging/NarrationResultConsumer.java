package vn.edu.cnpm.narration.messaging;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import vn.edu.cnpm.narration.service.NarrationStore;
import java.io.IOException;
import java.util.Set;
@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class NarrationResultConsumer {
    private static final Set<String> ERROR_CODES=Set.of("TIMEOUT","PROVIDER_ERROR","CONTENT_NOT_FOUND","STORAGE_ERROR","INTERNAL_ERROR");
    private final ObjectMapper mapper;private final NarrationStore store;private final CallbackClient callback;
    public NarrationResultConsumer(ObjectMapper mapper,NarrationStore store,CallbackClient callback,Environment env){
        this.mapper=mapper;this.store=store;this.callback=callback;
    }
    @RabbitListener(queues="#{narrationTranslationCompletedQueue.name}",ackMode="MANUAL")
    public void translationCompleted(Message message,Channel channel)throws IOException {
        consume(message,channel,"translation.completed");
    }
    @RabbitListener(queues="#{narrationResultsQueue.name}",ackMode="MANUAL")
    public void result(Message message,Channel channel)throws IOException {
        String route=message.getMessageProperties().getReceivedRoutingKey();
        consume(message,channel,route);
    }
    private void consume(Message message,Channel channel,String route)throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        String eventId=String.valueOf(message.getMessageProperties().getHeaders().get("eventId"));
        String correlation=(String)message.getMessageProperties().getHeaders().get("correlationId");
        JsonNode event;
        try{
            event=mapper.readTree(message.getBody());
            if(eventId.equals("null")||correlation==null||event==null||!event.isObject())throw new IllegalArgumentException("Missing event metadata");
            if(event.path("jobId").asText().isBlank()||event.path("targetId").asText().isBlank())throw new IllegalArgumentException("Missing target reference");
            if("translation.completed".equals(route)&&event.path("translationId").asText().isBlank())throw new IllegalArgumentException("Missing translationId");
            if("tts.completed".equals(route)&&(event.path("translationId").asText().isBlank()||event.path("audioId").asText().isBlank()))throw new IllegalArgumentException("Missing audio result");
            if(route.endsWith(".failed")&&!ERROR_CODES.contains(event.path("errorCode").asText()))throw new IllegalArgumentException("Invalid errorCode");
            if(!Set.of("translation.completed","translation.failed","tts.completed","tts.failed").contains(route))throw new IllegalArgumentException("Unknown routing key");
        }catch(Exception ex){channel.basicNack(tag,false,false);return;}
        try{
            String jobId=event.path("jobId").asText(),targetId=event.path("targetId").asText();
            java.util.Map<String,Object> job=null;
            if("translation.completed".equals(route))job=store.applyTranslationCompleted(jobId,targetId,event.path("translationId").asText());
            else if("tts.completed".equals(route))job=store.applyTtsCompleted(jobId,targetId,event.path("translationId").asText(),event.path("audioId").asText());
            else job=store.applyFailure(jobId,targetId,event.path("errorCode").asText());
            store.clearAttempt(eventId);
            if(job!=null)callback.notify(jobId,correlation,job);
            channel.basicAck(tag,false);
        }catch(Exception ex){
            int attempts;
            try{attempts=store.nextInternalAttempt(eventId);}
            catch(Exception databaseUnavailable){channel.basicNack(tag,false,false);return;}
            try{Thread.sleep(100L*attempts);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            channel.basicNack(tag,false,attempts<=3);
        }
    }
}
