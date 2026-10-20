package vn.edu.cnpm.translation.api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.cnpm.translation.provider.TranslationProvider;
import vn.edu.cnpm.translation.service.TranslationStore;
import java.util.Map;
@RestController
public class TranslationController {
    private final TranslationStore store;
    private final TranslationProvider provider;
    public TranslationController(TranslationStore store, TranslationProvider provider) {
        this.store = store;
        this.provider = provider;
    }
    @GetMapping("/internal/translations/{translationId}")
    public ApiEnvelope<Map<String,Object>> get(@PathVariable String translationId) {
        return ApiEnvelope.success(store.get(translationId));
    }
    @PostMapping("/internal/translations/translate")
    public ApiEnvelope<Map<String,Object>> translate(@RequestBody Map<String,Object> req) {
        String contentId = (String) req.get("contentId");
        int version = ((Number) req.get("version")).intValue();
        String sourceLang = (String) req.get("sourceLang");
        String targetLang = (String) req.get("targetLang");
        String text = (String) req.get("text");

        Map<String, Object> cached = store.findCached(contentId, version, targetLang);
        if (cached != null) {
            return ApiEnvelope.success(Map.of(
                    "translationId", cached.get("translationId"),
                    "translatedText", cached.get("textContent")
            ));
        }

        String translatedText;
        if (targetLang != null && targetLang.equalsIgnoreCase(sourceLang)) {
            translatedText = text;
        } else {
            translatedText = provider.translate(text, sourceLang, targetLang);
        }
        Map<String, Object> saved = store.save(contentId, version, targetLang, translatedText);
        return ApiEnvelope.success(Map.of(
                "translationId", saved.get("translationId"),
                "translatedText", translatedText
        ));
    }
}