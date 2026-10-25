package vn.edu.cnpm.translation.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.edu.cnpm.translation.provider.TranslationProvider;
import vn.edu.cnpm.translation.service.TranslationStore;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class TranslationConsumer {
    private final ObjectMapper mapper;
    private final TranslationStore store;
    private final TranslationProvider provider;
    private final EventPublisher events;
    private final RestClient contentClient;
    private final String contentToken;
    private final int chunkSize;

    public TranslationConsumer(ObjectMapper mapper,TranslationStore store,TranslationProvider provider,
                               EventPublisher events,Environment env) {
        this.mapper=mapper;this.store=store;this.provider=provider;this.events=events;
        this.contentClient=RestClient.builder().baseUrl(env.getProperty("CONTENT_SERVICE_URL","http://localhost:8082"))
                .build();
        this.contentToken=env.getProperty("CONTENT_SERVICE_TOKEN","content-token");
        this.chunkSize=Math.max(256,env.getProperty("TRANSLATION_CHUNK_SIZE",Integer.class,3000));
    }

    @RabbitListener(queues="#{translationRequestedQueue.name}",ackMode="MANUAL")
    public void requested(Message message,Channel channel) throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        Object eventHeader=message.getMessageProperties().getHeaders().get("eventId");
        String eventId=eventHeader==null?null:String.valueOf(eventHeader);
        String correlationId=(String)message.getMessageProperties().getHeaders().get("correlationId");
        NarrationRequested event;
        try {
            event=mapper.readValue(message.getBody(),NarrationRequested.class);
            validate(event);
        } catch(Exception ex) {
            channel.basicNack(tag,false,false);
            return;
        }
        try {
            if(eventId==null||correlationId==null)throw new IllegalArgumentException("Missing event headers");
            if(!store.isCancelled(event.jobId())) {
                process(event,correlationId);
            }
            store.clearAttempt(eventId);
            channel.basicAck(tag,false);
        } catch(Exception ex) {
            int attempts;
            try { attempts=store.nextInternalAttempt(eventId); }
            catch(Exception databaseUnavailable) { channel.basicNack(tag,false,false);return; }
            channel.basicNack(tag,false,attempts<=3);
        }
    }

    @RabbitListener(queues="#{cancelQueue.name}",ackMode="MANUAL")
    public void cancelled(Message message,Channel channel) throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        try {
            Map<?,?> event=mapper.readValue(message.getBody(),Map.class);
            Object jobId=event.get("jobId");
            if(!(jobId instanceof String id)||id.isBlank()) throw new IllegalArgumentException("jobId is required");
            store.cancel(id);
            channel.basicAck(tag,false);
        } catch(Exception ex) {
            channel.basicNack(tag,false,false);
        }
    }

    private void process(NarrationRequested event,String correlationId) {
        Map<String,Object> cached=store.findCached(event.contentId(),event.version(),event.lang());
        Map<String,Object> translation=cached;
        if(translation==null) {
            Map<String,Object> source;
            try{source=readExactContent(event,correlationId);}
            catch(ContentUnavailableException ex){
                events.publish("translation.failed",new TranslationFailed(event.jobId(),event.targetId(),event.lang(),"INTERNAL_ERROR"),correlationId);return;
            }
            if(source==null) {
                events.publish("translation.failed",new TranslationFailed(event.jobId(),event.targetId(),event.lang(),"CONTENT_NOT_FOUND"),correlationId);
                return;
            }
            String text;
            if(event.lang().equals(source.get("sourceLang"))) {
                text=String.valueOf(source.get("textContent"));
            } else {
                text=translateWithRetry(String.valueOf(source.get("textContent")),
                        String.valueOf(source.get("sourceLang")),event.lang());
                if(text==null) {
                    events.publish("translation.failed",new TranslationFailed(event.jobId(),event.targetId(),event.lang(),"PROVIDER_ERROR"),correlationId);
                    return;
                }
            }
            if(store.isCancelled(event.jobId())) return;
            translation=store.save(event.contentId(),event.version(),event.lang(),text);
        }
        if(store.isCancelled(event.jobId())) return;
        events.publish("translation.completed",new TranslationCompleted(event.jobId(),event.targetId(),
                String.valueOf(translation.get("translationId")),event.lang(),event.voiceId()),correlationId);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readExactContent(NarrationRequested event,String correlationId) {
        for(int attempt=0;attempt<4;attempt++) {
            try {
                Map<String,Object> envelope=contentClient.get()
                        .uri("/internal/contents/{contentId}/versions/{version}",event.contentId(),event.version())
                        .header("X-Service-Token",contentToken)
                        .header("X-Correlation-ID",correlationId==null?"":correlationId)
                        .retrieve().body(Map.class);
                return envelope==null?null:(Map<String,Object>)envelope.get("data");
            } catch(RestClientResponseException ex) {
                if(ex.getStatusCode().value()==404) return null;
                if(attempt==3) throw new ContentUnavailableException(ex);
                pause(attempt);
            } catch(Exception ex) {
                if(attempt==3) throw new ContentUnavailableException(ex);
                pause(attempt);
            }
        }
        return null;
    }

    private String translateWithRetry(String source,String sourceLang,String targetLang) {
        for(int attempt=0;attempt<4;attempt++) {
            try {
                StringBuilder result=new StringBuilder();
                for(String chunk:chunks(source,chunkSize)) result.append(provider.translate(chunk,sourceLang,targetLang));
                return result.toString();
            } catch(Exception ex) {
                if(attempt==3) return null;
                pause(attempt);
            }
        }
        return null;
    }

    private java.util.List<String> chunks(String text,int size) {
        java.util.ArrayList<String> result=new java.util.ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=Math.min(text.length(),start+size);
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            result.add(text.substring(start,end));start=end;
        }
        return result;
    }
    private void pause(int attempt) {
        try { Thread.sleep(Duration.ofMillis(100L*(attempt+1)).toMillis()); }
        catch(InterruptedException ex) { Thread.currentThread().interrupt();throw new IllegalStateException(ex); }
    }
    private void validate(NarrationRequested event) {
        if(event==null||blank(event.jobId())||blank(event.targetId())||blank(event.contentId())||
                event.version()<1||blank(event.lang())||blank(event.voiceId()))throw new IllegalArgumentException("Malformed narration.requested");
    }
    private boolean blank(String value) { return value==null||value.isBlank(); }
    private static final class ContentUnavailableException extends RuntimeException {
        ContentUnavailableException(Throwable cause){super(cause);}
    }

    public record NarrationRequested(String jobId,String targetId,String contentId,int version,String lang,String voiceId) {}
    public record TranslationCompleted(String jobId,String targetId,String translationId,String lang,String voiceId) {}
    public record TranslationFailed(String jobId,String targetId,String lang,String errorCode) {}
}
