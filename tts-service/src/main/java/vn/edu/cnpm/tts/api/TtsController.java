package vn.edu.cnpm.tts.api;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.tts.service.TtsStore;
import vn.edu.cnpm.tts.storage.SignedUrlService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import vn.edu.cnpm.tts.config.LanguageRegistry;

@RestController
public class TtsController {
    private final TtsStore store;
    private final SignedUrlService urls;
    private final org.springframework.core.env.Environment env;

    public TtsController(TtsStore store, SignedUrlService urls, org.springframework.core.env.Environment env) {
        this.store = store;
        this.urls = urls;
        this.env = env;
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
