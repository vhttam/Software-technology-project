package vn.sgu.narration.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import vn.sgu.narration.domain.JobTargetEntity;
import vn.sgu.narration.domain.TargetStatus;
import vn.sgu.narration.integration.NarrationDependencies;
import vn.sgu.narration.repository.JobTargetRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/internal/narrations")
public class ClientNarrationController {
    private final ClientNarrationService narration;

    public ClientNarrationController(ClientNarrationService narration) { this.narration = narration; }

    @GetMapping
    public ApiEnvelope<Contracts.PublishedNarrations> list(@RequestParam String contentId) {
        return ApiEnvelope.success(narration.list(contentId));
    }

    @GetMapping("/{lang}")
    public ApiEnvelope<Contracts.NarrationArtifact> get(@RequestParam String contentId, @PathVariable String lang,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        return ApiEnvelope.success(narration.get(contentId, lang, correlationId));
    }
}

@Service
class ClientNarrationService {
    private final NarrationDependencies dependencies;
    private final JobTargetRepository targets;

    ClientNarrationService(NarrationDependencies dependencies, JobTargetRepository targets) {
        this.dependencies = dependencies;
        this.targets = targets;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Contracts.PublishedNarrations list(String contentId) {
        LinkedHashMap<String, Contracts.LanguageView> latest = new LinkedHashMap<>();
        for (JobTargetEntity target : targets.findPublishedByContent(contentId, TargetStatus.PUBLISHED)) {
            latest.putIfAbsent(target.getLang().toLowerCase(Locale.ROOT),
                    new Contracts.LanguageView(target.getLang(), target.getJob().getContentVersion()));
        }
        List<Contracts.LanguageView> languages = latest.values().stream()
                .sorted((a, b) -> a.lang().compareToIgnoreCase(b.lang())).toList();
        return new Contracts.PublishedNarrations(languages);
    }

    public Contracts.NarrationArtifact get(String contentId, String lang, String correlationId) {
        JobTargetEntity selected = targets.findPublishedByContent(contentId, TargetStatus.PUBLISHED).stream()
                .filter(target -> target.getLang().equalsIgnoreCase(lang)).findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "NARRATION_NOT_FOUND",
                        "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn"));
        if (selected.getTranslationId() == null || selected.getAudioId() == null) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "NARRATION_ARTIFACT_UNAVAILABLE",
                    "Artifact của target PUBLISHED chưa sẵn sàng");
        }
        Contracts.TranslationView translated = dependencies.translation(selected.getTranslationId(), contentId,
                selected.getJob().getContentVersion(), selected.getLang(), correlationId);
        Contracts.SignedUrl signedUrl = dependencies.signedUrl(selected.getAudioId(), correlationId);
        if (!contentId.equals(translated.contentId())
                || selected.getJob().getContentVersion() != translated.version()
                || !selected.getLang().equalsIgnoreCase(translated.lang())) {
            throw new AppException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "TRANSLATION_MISMATCH", "Bản dịch không khớp với target PUBLISHED đã chọn");
        }
        return new Contracts.NarrationArtifact(selected.getLang(), selected.getJob().getContentVersion(),
                translated.textContent(), signedUrl.audioUrl(), signedUrl.expiresAt());
    }
}
