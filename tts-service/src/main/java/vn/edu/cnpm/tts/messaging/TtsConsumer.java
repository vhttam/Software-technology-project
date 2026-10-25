package vn.edu.cnpm.tts.messaging;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.edu.cnpm.tts.provider.TtsProvider;
import vn.edu.cnpm.tts.service.TtsStore;
import vn.edu.cnpm.tts.storage.ObjectStorage;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
@ConditionalOnProperty(name="app.messaging.enabled",havingValue="true")
public class TtsConsumer {
    private final ObjectMapper mapper;private final TtsStore store;private final TtsProvider provider;
    private final ObjectStorage storage;private final EventPublisher events;private final RestClient translations;
    private final String serviceToken;private final int chunkSize;
    private final vn.edu.cnpm.tts.service.AudioQualityValidator audioValidator;
    public TtsConsumer(ObjectMapper mapper,TtsStore store,TtsProvider provider,ObjectStorage storage,
                       EventPublisher events,Environment env,vn.edu.cnpm.tts.service.AudioQualityValidator audioValidator) {
        this.mapper=mapper;this.store=store;this.provider=provider;this.storage=storage;this.events=events;
        this.audioValidator=audioValidator;
        translations=RestClient.builder().baseUrl(env.getProperty("TRANSLATION_SERVICE_URL","http://localhost:8084")).build();
        serviceToken=env.getProperty("TTS_SERVICE_TOKEN","tts-token");
        chunkSize=Math.max(256,env.getProperty("TTS_CHUNK_SIZE",Integer.class,3000));
    }
    @RabbitListener(queues="#{ttsTranslationCompletedQueue.name}",ackMode="MANUAL")
    public void completed(Message message,Channel channel)throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        String eventId=String.valueOf(message.getMessageProperties().getHeaders().get("eventId"));
        String correlation=(String)message.getMessageProperties().getHeaders().get("correlationId");
        TranslationCompleted event;
        try {event=mapper.readValue(message.getBody(),TranslationCompleted.class);validate(event);}
        catch(Exception ex){ System.err.println("[TtsConsumer] JSON read failed: " + ex); ex.printStackTrace(); channel.basicNack(tag,false,false); return; }
        try {
            if(!store.isCancelled(event.jobId()))process(event,correlation);
            store.clearAttempt(eventId);channel.basicAck(tag,false);
        } catch(Exception ex) {
            int attempts;
            try{attempts=store.nextInternalAttempt(eventId);}
            catch(Exception db){ System.err.println("[TtsConsumer] DB attempt failed: " + db); db.printStackTrace(); channel.basicNack(tag,false,false); return; }
            System.err.println("[TtsConsumer] Process failed: " + ex); ex.printStackTrace(); channel.basicNack(tag,false,attempts<=3);
        }
    }
    @RabbitListener(queues="#{cancelQueue.name}",ackMode="MANUAL")
    public void cancelled(Message message,Channel channel)throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        try {
            Map<?,?> event=mapper.readValue(message.getBody(),Map.class);Object value=event.get("jobId");
            if(!(value instanceof String jobId)||jobId.isBlank())throw new IllegalArgumentException("jobId is required");
            store.cancel(jobId);channel.basicAck(tag,false);
        } catch(Exception ex){channel.basicNack(tag,false,false);}
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> translation(String id,String correlation) {
        for(int attempt=0;attempt<4;attempt++) {
            try {
                Map<String,Object> response=translations.get().uri("/internal/translations/{id}",id)
                        .header("X-Service-Token",serviceToken).header("X-Correlation-ID",correlation==null?"":correlation)
                        .retrieve().body(Map.class);
                return response==null?null:(Map<String,Object>)response.get("data");
            } catch(RestClientResponseException ex) {
                if(ex.getStatusCode().value()==404)return null;
                if(attempt==3)throw ex;pause(attempt);
            } catch(Exception ex){if(attempt==3)throw ex;pause(attempt);}
        }
        return null;
    }
    private void process(TranslationCompleted event,String correlation) {
        Map<String,Object> cached=store.findAudio(event.translationId(),event.voiceId());
        if(cached!=null) {
            events.publish("tts.completed",new TtsCompleted(event.jobId(),event.targetId(),event.translationId(),
                    String.valueOf(cached.get("audioId")),event.lang()),correlation);return;
        }
        Map<String,Object> translated=translation(event.translationId(),correlation);
        if(translated==null) {
            events.publish("tts.failed",new TtsFailed(event.jobId(),event.targetId(),event.lang(),"CONTENT_NOT_FOUND"),correlation);return;
        }
        String text=String.valueOf(translated.get("textContent"));byte[] bytes;
        try{bytes=synthesize(text,event.lang(),event.voiceId());}
        catch(ProviderFailure ex){
            events.publish("tts.failed",new TtsFailed(event.jobId(),event.targetId(),event.lang(),"PROVIDER_ERROR"),correlation);return;
        }
        if(store.isCancelled(event.jobId()))return;

        // Post-synthesis quality validation
        java.io.File tempAudio = null;
        try {
            tempAudio = java.io.File.createTempFile("tts_val_", ".mp3");
            java.nio.file.Files.write(tempAudio.toPath(), bytes);
            double estimatedDuration = (double) bytes.length / 4000.0;
            if (!audioValidator.validate(event.lang(), text.length(), estimatedDuration, tempAudio)) {
                events.publish("tts.failed", new TtsFailed(event.jobId(), event.targetId(), event.lang(), "PROVIDER_ERROR"), correlation);
                return;
            }
        } catch (Exception ex) {
            events.publish("tts.failed", new TtsFailed(event.jobId(), event.targetId(), event.lang(), "PROVIDER_ERROR"), correlation);
            return;
        } finally {
            if (tempAudio != null) tempAudio.delete();
        }

        String key=putWithRetry(bytes);
        if(key==null) {
            events.publish("tts.failed",new TtsFailed(event.jobId(),event.targetId(),event.lang(),"STORAGE_ERROR"),correlation);return;
        }
        if(store.isCancelled(event.jobId())){deleteQuietly(key);return;}
        int duration=Math.max(1,(int) Math.round((double) bytes.length / 4000.0));
        Map<String,Object> saved=store.save(event.translationId(),event.voiceId(),key,duration,bytes.length);
        if(!key.equals(saved.get("objectKey")))deleteQuietly(key);
        events.publish("tts.completed",new TtsCompleted(event.jobId(),event.targetId(),event.translationId(),
                String.valueOf(saved.get("audioId")),event.lang()),correlation);
    }
    private byte[] synthesize(String text,String lang,String voice) {
        for(int attempt=0;attempt<4;attempt++) {
            try {
                java.io.ByteArrayOutputStream result=new java.io.ByteArrayOutputStream();
                for(String chunk:chunks(text))result.writeBytes(provider.synthesize(chunk,lang,voice));
                return result.toByteArray();
            } catch(Exception ex){if(attempt==3)throw new ProviderFailure(ex);pause(attempt);}
        }
        return new byte[0];
    }
    private String putWithRetry(byte[] bytes) {
        for(int attempt=0;attempt<4;attempt++) {
            try{return storage.put(bytes);}catch(Exception ex){if(attempt==3)return null;pause(attempt);}
        }
        return null;
    }
    private java.util.List<String> chunks(String text) {
        java.util.ArrayList<String> result=new java.util.ArrayList<>();
        for(int start=0;start<text.length();) {
            int end=Math.min(text.length(),start+chunkSize);
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            result.add(text.substring(start,end));start=end;
        }
        return result;
    }
    private void deleteQuietly(String key){try{storage.delete(key);}catch(Exception ignored){}}
    private void pause(int attempt) {
        try{Thread.sleep(Duration.ofMillis(100L*(attempt+1)).toMillis());}
        catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}
    }
    private void validate(TranslationCompleted e) {
        if(e==null||blank(e.jobId())||blank(e.targetId())||blank(e.translationId())||blank(e.lang())||blank(e.voiceId()))
            throw new IllegalArgumentException("Malformed translation.completed");
    }
    private boolean blank(String s){return s==null||s.isBlank();}
    public record TranslationCompleted(String jobId,String targetId,String translationId,String lang,String voiceId){}
    public record TtsCompleted(String jobId,String targetId,String translationId,String audioId,String lang){}
    public record TtsFailed(String jobId,String targetId,String lang,String errorCode){}
    private static final class ProviderFailure extends RuntimeException {
        ProviderFailure(Throwable cause){super(cause);}
    }
}
