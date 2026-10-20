package vn.edu.cnpm.translation.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "stub", matchIfMissing = true)
public class StubTranslationProvider implements TranslationProvider {
    @Override public String translate(String text,String sourceLang,String targetLang) {
        if (sourceLang != null && sourceLang.trim().equalsIgnoreCase(targetLang != null ? targetLang.trim() : "")) {
            return text;
        }
        return "["+targetLang+" stub] "+text;
    }
}
