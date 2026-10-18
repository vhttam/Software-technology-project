package vn.edu.cnpm.translation.api;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.cnpm.translation.service.TranslationStore;
import java.util.Map;
@RestController
public class TranslationController {
    private final TranslationStore store;
    public TranslationController(TranslationStore store) { this.store=store; }
    @GetMapping("/internal/translations/{translationId}")
    public ApiEnvelope<Map<String,Object>> get(@PathVariable String translationId) {
        return ApiEnvelope.success(store.get(translationId));
    }
}
