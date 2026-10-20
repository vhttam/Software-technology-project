package vn.edu.cnpm.tts.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.tts.service.TtsStore;
import vn.edu.cnpm.tts.storage.SignedUrlService;
import vn.edu.cnpm.tts.storage.ObjectStorage;
import vn.edu.cnpm.tts.provider.TtsProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.edu.cnpm.tts.config.LanguageRegistry;

@RestController
public class TtsController {
    private final TtsStore store;
    private final SignedUrlService urls;
    private final org.springframework.core.env.Environment env;
    private final TtsProvider provider;
    private final ObjectStorage storage;

    public TtsController(TtsStore store, SignedUrlService urls, org.springframework.core.env.Environment env,
                         TtsProvider provider, ObjectStorage storage) {
        this.store = store;
        this.urls = urls;
        this.env = env;
        this.provider = provider;
        this.storage = storage;
    }

    @PostMapping("/internal/tts/synthesize")
    public ApiEnvelope<Map<String, Object>> synthesize(@RequestBody Map<String, Object> req) {
        String translationId = (String) req.get("translationId");
        String lang = (String) req.get("lang");
        String voiceId = (String) req.get("voiceId");
        String text = (String) req.get("text");

        Map<String, Object> cached = store.findAudio(translationId, voiceId);
        if (cached != null) {
            String key = (String) cached.get("objectKey");
            try {
                storage.get(key);
                return ApiEnvelope.success(cached);
            } catch (Exception ex) {
                // File deleted or missing on disk, fall through to re-synthesize!
            }
        }

        byte[] bytes = provider.synthesize(text, lang, voiceId);
        try {
            String key = storage.put(bytes);
            int duration = Math.max(1, (int) Math.round((double) bytes.length / 4000.0));
            Map<String, Object> saved = store.save(translationId, voiceId, key, duration, bytes.length);
            return ApiEnvelope.success(saved);
        } catch (Exception e) {
            throw new RuntimeException("TTS storage error", e);
        }
    }

    @GetMapping("/internal/voices")
    public ApiEnvelope<Map<String,Object>> voices(){
        boolean isMock = "stub".equalsIgnoreCase(env.getProperty("app.tts.provider", "stub"));
        return ApiEnvelope.success(Map.of(
                "supportedVoices", LanguageRegistry.getSupportedVoicesResponse(),
                "supportsVoiceSelection", false,
                "isMock", isMock
        ));
    }

    @GetMapping("/internal/audios/{audioId}/signed-url")
    public ApiEnvelope<Map<String,Object>> signedUrl(@PathVariable String audioId){
        Map<String,Object> audio=store.getAudio(audioId);
        Instant expiry=urls.expiresAt();
        String url=urls.createUrl(String.valueOf(audio.get("objectKey")),expiry);
        return ApiEnvelope.success(Map.of("audioUrl",url,"expiresAt",expiry.toString()));
    }

    public static ApiException notFound(String id){return new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy dữ liệu media");}
}
