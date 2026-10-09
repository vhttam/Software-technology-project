package vn.sgu.narration.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class Contracts {
    private Contracts() {}

    public record TargetInput(
            @NotBlank @Size(max = 32) String lang,
            @NotBlank @Size(max = 128) String voiceId) {}

    public record CreateJobRequest(
            @NotBlank @Size(max = 128) String contentId,
            @Size(max = 64) String retryOfJobId,
            @NotEmpty List<@Valid TargetInput> targets) {}

    public record TargetView(
            String targetId, String lang, String voiceId, String status,
            String errorCode, Instant updatedAt) {}

    public record JobView(
            String jobId, String contentId, int version, String status,
            Instant createdAt, Instant updatedAt, List<TargetView> targets) {}

    public record ContentCheck(String contentId, Integer version, Boolean isDeleted) {}
    public record VoiceGroup(String lang, List<String> voices) {}
    public record VoiceCatalog(List<VoiceGroup> supportedVoices) {}
    public record TranslationView(String translationId, String contentId, Integer version,
                                  String lang, String textContent) {}
    public record SignedUrl(String audioUrl, Instant expiresAt) {}
    public record LanguageView(String lang, int version) {}
    public record PublishedNarrations(List<LanguageView> narrations) {}
    public record NarrationArtifact(String lang, int version, String subtitle,
                                    String audioUrl, Instant audioUrlExpiresAt) {}
    public record ProgressTarget(String targetId, String lang, String status) {}
    public record JobProgressEvent(String event, String correlationId, ProgressData data) {}
    public record ProgressData(String jobId, String status, Instant updatedAt,
                               List<ProgressTarget> targets) {}

    public record TranslationCompleted(String jobId, String targetId, String translationId,
                                       String lang, String voiceId) {}
    public record TranslationFailed(String jobId, String targetId, String lang, String errorCode) {}
    public record TtsCompleted(String jobId, String targetId, String translationId,
                               String audioId, String lang) {}
    public record TtsFailed(String jobId, String targetId, String lang, String errorCode) {}
    public record NarrationRequested(String jobId, String targetId, String contentId,
                                     int version, String lang, String voiceId) {}
    public record NarrationCancelled(String jobId) {}
}
