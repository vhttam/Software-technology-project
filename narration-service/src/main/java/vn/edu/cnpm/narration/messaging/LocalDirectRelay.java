package vn.edu.cnpm.narration.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.cnpm.narration.service.NarrationStore;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name="app.messaging.enabled", havingValue="false", matchIfMissing=true)
public class LocalDirectRelay {
    private static final Logger log = LoggerFactory.getLogger(LocalDirectRelay.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final NarrationStore store;
    private final CallbackClient callback;
    private final RestClient contentClient;
    private final RestClient translationClient;
    private final RestClient ttsClient;
    private final String serviceToken;

    public LocalDirectRelay(JdbcTemplate jdbc, ObjectMapper mapper, NarrationStore store,
                            CallbackClient callback, Environment env) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.store = store;
        this.callback = callback;
        this.contentClient = RestClient.builder().baseUrl(env.getProperty("CONTENT_SERVICE_URL", "http://localhost:8082")).build();
        this.translationClient = RestClient.builder().baseUrl(env.getProperty("TRANSLATION_SERVICE_URL", "http://localhost:8084")).build();
        this.ttsClient = RestClient.builder().baseUrl(env.getProperty("TTS_SERVICE_URL", "http://localhost:8085")).build();
        this.serviceToken = env.getProperty("NARRATION_SERVICE_TOKEN", "narration-token");
    }

    @Scheduled(fixedDelay = 1500)
    public void processPendingEvents() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_id, routing_key, payload, correlation_id FROM outbox_events WHERE published = 0 ORDER BY occurred_at");
        for (Map<String, Object> row : rows) {
            processEvent(row);
        }
    }

    @SuppressWarnings("unchecked")
    private void processEvent(Map<String, Object> row) {
        String eventId = String.valueOf(row.get("event_id"));
        String routingKey = String.valueOf(row.get("routing_key"));
        String correlationId = row.get("correlation_id") != null ? String.valueOf(row.get("correlation_id")) : "";

        try {
            JsonNode payload = mapper.readTree(String.valueOf(row.get("payload")));

            if ("narration.requested".equals(routingKey)) {
                String jobId = payload.path("jobId").asText();
                String targetId = payload.path("targetId").asText();
                String contentId = payload.path("contentId").asText();
                int version = payload.path("version").asInt();
                String lang = payload.path("lang").asText();
                String voiceId = payload.path("voiceId").asText();

                Map<String, Object> job = store.markRequested(targetId);
                if (job == null) {
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }
                callback.notify(jobId, correlationId, job);

                // 1. Fetch content version from content-service
                Map<String, Object> contentEnvelope;
                try {
                    contentEnvelope = contentClient.get()
                            .uri("/internal/contents/{id}/versions/{version}", contentId, version)
                            .header("X-Service-Token", serviceToken)
                            .header("X-Correlation-ID", correlationId)
                            .retrieve()
                            .body(Map.class);
                } catch (Exception e) {
                    log.error("Failed to fetch content for narration: {}", e.getMessage());
                    job = store.applyFailure(jobId, targetId, "CONTENT_NOT_FOUND");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                if (contentEnvelope == null || !(contentEnvelope.get("data") instanceof Map<?, ?> contentData)) {
                    job = store.applyFailure(jobId, targetId, "CONTENT_NOT_FOUND");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                String sourceLang = String.valueOf(contentData.get("sourceLang"));
                String textContent = String.valueOf(contentData.get("textContent"));

                // 2. Call translation-service
                Map<String, Object> translationEnvelope;
                try {
                    translationEnvelope = translationClient.post()
                            .uri("/internal/translations/translate")
                            .header("X-Service-Token", serviceToken)
                            .header("X-Correlation-ID", correlationId)
                            .body(Map.of(
                                    "contentId", contentId,
                                    "version", version,
                                    "sourceLang", sourceLang,
                                    "targetLang", lang,
                                    "text", textContent
                            ))
                            .retrieve()
                            .body(Map.class);
                } catch (Exception e) {
                    log.error("Failed to translate: {}", e.getMessage());
                    job = store.applyFailure(jobId, targetId, "PROVIDER_ERROR");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                if (translationEnvelope == null || !(translationEnvelope.get("data") instanceof Map<?, ?> transData)) {
                    job = store.applyFailure(jobId, targetId, "PROVIDER_ERROR");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                String translationId = String.valueOf(transData.get("translationId"));
                String translatedText = String.valueOf(transData.get("translatedText"));

                // Mark translation completed
                job = store.applyTranslationCompleted(jobId, targetId, translationId);
                if (job != null) callback.notify(jobId, correlationId, job);

                // 3. Call tts-service
                Map<String, Object> ttsEnvelope;
                try {
                    ttsEnvelope = ttsClient.post()
                            .uri("/internal/tts/synthesize")
                            .header("X-Service-Token", serviceToken)
                            .header("X-Correlation-ID", correlationId)
                            .body(Map.of(
                                    "translationId", translationId,
                                    "lang", lang,
                                    "voiceId", voiceId,
                                    "text", translatedText
                            ))
                            .retrieve()
                            .body(Map.class);
                } catch (Exception e) {
                    log.error("Failed to synthesize TTS: {}", e.getMessage());
                    job = store.applyFailure(jobId, targetId, "PROVIDER_ERROR");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                if (ttsEnvelope == null || !(ttsEnvelope.get("data") instanceof Map<?, ?> ttsData)) {
                    job = store.applyFailure(jobId, targetId, "PROVIDER_ERROR");
                    if (job != null) callback.notify(jobId, correlationId, job);
                    jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
                    return;
                }

                String audioId = String.valueOf(ttsData.get("audioId"));

                // Mark TTS completed
                job = store.applyTtsCompleted(jobId, targetId, translationId, audioId);
                if (job != null) callback.notify(jobId, correlationId, job);

                // Mark outbox event published
                jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
            } else {
                // Other events like narration.cancel
                jdbc.update("UPDATE outbox_events SET published=1 WHERE event_id=?", eventId);
            }
        } catch (Exception ex) {
            log.error("Error processing outbox event {}: {}", eventId, ex.getMessage(), ex);
        }
    }
}
