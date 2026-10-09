package vn.sgu.narration.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.sgu.narration.api.ApiEnvelope;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class NarrationDependencies {
    private final RestClient content;
    private final RestClient translation;
    private final RestClient tts;
    private final String serviceToken;
    private final boolean mocksEnabled;

    private static final ParameterizedTypeReference<ApiEnvelope<Contracts.ContentCheck>> CONTENT_CHECK = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<Contracts.VoiceCatalog>> VOICES = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<Contracts.TranslationView>> TRANSLATION = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<ApiEnvelope<Contracts.SignedUrl>> SIGNED_URL = new ParameterizedTypeReference<>() {};

    public NarrationDependencies(RestClient.Builder builder,
            @Value("${app.downstream.content-base-url}") String contentUrl,
            @Value("${app.downstream.translation-base-url}") String translationUrl,
            @Value("${app.downstream.tts-base-url}") String ttsUrl,
            @Value("${app.downstream.service-token}") String serviceToken,
            @Value("${app.mocks.enabled:true}") boolean mocksEnabled) {
        this.content = builder.baseUrl(contentUrl).build();
        this.translation = builder.baseUrl(translationUrl).build();
        this.tts = builder.baseUrl(ttsUrl).build();
        this.serviceToken = serviceToken;
        this.mocksEnabled = mocksEnabled;
    }

    public Contracts.ContentCheck validateContentForJob(String contentId, String correlationId) {
        if (mocksEnabled) {
            if (contentId.startsWith("missing-")) throw notFound("CONTENT_NOT_FOUND", "Nội dung không tồn tại hoặc đã bị xóa mềm");
            return new Contracts.ContentCheck(contentId, 3, contentId.startsWith("deleted-"));
        }
        try {
            ApiEnvelope<Contracts.ContentCheck> response = content.post()
                    .uri("/internal/contents/for-job")
                    .headers(headers -> addInternalHeaders(headers, correlationId))
                    .body(Map.of("contentId", contentId))
                    .retrieve().body(CONTENT_CHECK);
            if (response == null || response.data() == null) {
                throw unavailable("CONTENT_SERVICE_UNAVAILABLE", "Content Service trả dữ liệu không hợp lệ",
                        new IllegalStateException("Empty content check response"));
            }
            return response.data();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw notFound("CONTENT_NOT_FOUND", "Nội dung không tồn tại hoặc đã bị xóa mềm");
            throw unavailable("CONTENT_SERVICE_UNAVAILABLE", "Content Service tạm thời không khả dụng", ex);
        } catch (Exception ex) {
            throw unavailable("CONTENT_SERVICE_UNAVAILABLE", "Content Service tạm thời không khả dụng", ex);
        }
    }

    public Contracts.VoiceCatalog voices(String correlationId) {
        if (mocksEnabled) {
            return new Contracts.VoiceCatalog(List.of(
                    new Contracts.VoiceGroup("en", List.of("en-US-Journey-F", "en-GB-Standard-A")),
                    new Contracts.VoiceGroup("fr", List.of("fr-FR-Standard-A")),
                    new Contracts.VoiceGroup("vi", List.of("vi-VN-Standard-A"))));
        }
        try {
            ApiEnvelope<Contracts.VoiceCatalog> response = tts.get().uri("/internal/voices")
                    .headers(headers -> addInternalHeaders(headers, correlationId)).retrieve().body(VOICES);
            if (response == null || response.data() == null) {
                throw unavailable("TTS_SERVICE_UNAVAILABLE", "TTS Service trả danh mục không hợp lệ",
                        new IllegalStateException("Empty voice catalog response"));
            }
            return response.data();
        } catch (Exception ex) {
            throw unavailable("TTS_SERVICE_UNAVAILABLE", "TTS Service tạm thời không khả dụng", ex);
        }
    }

    public Contracts.TranslationView translation(String translationId, String contentId, int version, String lang,
                                                 String correlationId) {
        if (mocksEnabled) {
            return new Contracts.TranslationView(translationId, contentId, version, lang,
                    "Mock translated subtitle for " + translationId);
        }
        try {
            ApiEnvelope<Contracts.TranslationView> response = translation.get()
                    .uri("/internal/translations/{translationId}", translationId)
                    .headers(headers -> addInternalHeaders(headers, correlationId))
                    .retrieve().body(TRANSLATION);
            if (response == null || response.data() == null) throw notFound("TRANSLATION_NOT_FOUND", "Không tìm thấy bản dịch");
            return response.data();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw notFound("TRANSLATION_NOT_FOUND", "Không tìm thấy bản dịch");
            throw unavailable("TRANSLATION_SERVICE_UNAVAILABLE", "Translation Service tạm thời không khả dụng", ex);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("TRANSLATION_SERVICE_UNAVAILABLE", "Translation Service tạm thời không khả dụng", ex);
        }
    }

    public Contracts.SignedUrl signedUrl(String audioId, String correlationId) {
        if (mocksEnabled) {
            return new Contracts.SignedUrl("https://example.invalid/mock-audio/" + safeId(audioId) + ".mp3?signature=mock",
                    Instant.now().plusSeconds(3600));
        }
        try {
            ApiEnvelope<Contracts.SignedUrl> response = tts.get()
                    .uri("/internal/audios/{audioId}/signed-url", audioId)
                    .headers(headers -> addInternalHeaders(headers, correlationId))
                    .retrieve().body(SIGNED_URL);
            if (response == null || response.data() == null) throw notFound("AUDIO_NOT_FOUND", "Không tìm thấy audio");
            return response.data();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw notFound("AUDIO_NOT_FOUND", "Không tìm thấy audio");
            throw unavailable("TTS_SERVICE_UNAVAILABLE", "TTS Service tạm thời không khả dụng", ex);
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("TTS_SERVICE_UNAVAILABLE", "TTS Service tạm thời không khả dụng", ex);
        }
    }

    private static String safeId(String value) {
        return value.replaceAll("[^A-Za-z0-9-]", "-");
    }

    private void addInternalHeaders(org.springframework.http.HttpHeaders headers, String correlationId) {
        headers.set("X-Service-Token", serviceToken);
        if (correlationId != null && !correlationId.isBlank()) headers.set("X-Correlation-ID", correlationId);
    }

    private static AppException notFound(String code, String message) {
        return new AppException(HttpStatus.NOT_FOUND, code, message);
    }

    private static AppException unavailable(String code, String message, Exception cause) {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE, code, message,
                Map.of("cause", cause.getClass().getSimpleName()));
    }
}
