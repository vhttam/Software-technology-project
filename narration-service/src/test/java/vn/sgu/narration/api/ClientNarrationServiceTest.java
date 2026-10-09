package vn.sgu.narration.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.sgu.narration.domain.JobTargetEntity;
import vn.sgu.narration.domain.NarrationJobEntity;
import vn.sgu.narration.domain.TargetStatus;
import vn.sgu.narration.integration.NarrationDependencies;
import vn.sgu.narration.repository.JobTargetRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ClientNarrationServiceTest {
    private NarrationDependencies dependencies;
    private JobTargetRepository targets;
    private ClientNarrationService service;

    @BeforeEach
    void setUp() {
        dependencies = mock(NarrationDependencies.class);
        targets = mock(JobTargetRepository.class);
        service = new ClientNarrationService(dependencies, targets);
    }

    @Test
    void listReturnsOnlyPublishedLanguagesAtNewestVersion() {
        JobTargetEntity frenchLatest = publishedTarget("j-fr-2", 2, "fr", "tr-fr", "au-fr");
        JobTargetEntity english = publishedTarget("j-en-1", 1, "en", "tr-en", "au-en");
        JobTargetEntity frenchOld = publishedTarget("j-fr-1", 1, "FR", "tr-fr-old", "au-fr-old");
        when(targets.findPublishedByContent("content-1", TargetStatus.PUBLISHED))
                .thenReturn(List.of(frenchLatest, english, frenchOld));

        Contracts.PublishedNarrations result = service.list("content-1");

        assertThat(result.narrations()).containsExactly(
                new Contracts.LanguageView("en", 1), new Contracts.LanguageView("fr", 2));
    }

    @Test
    void getReturnsSubtitleAndSignedAudioUrlForPublishedTarget() {
        JobTargetEntity target = publishedTarget("j-en-2", 2, "en", "tr-en", "au-en");
        when(targets.findPublishedByContent("content-1", TargetStatus.PUBLISHED)).thenReturn(List.of(target));
        Instant expiresAt = Instant.now().plusSeconds(600);
        when(dependencies.translation("tr-en", "content-1", 2, "en", "corr-1"))
                .thenReturn(new Contracts.TranslationView("tr-en", "content-1", 2, "en", "Hello"));
        when(dependencies.signedUrl("au-en", "corr-1"))
                .thenReturn(new Contracts.SignedUrl("https://audio.example/signed", expiresAt));

        Contracts.NarrationArtifact result = service.get("content-1", "EN", "corr-1");

        assertThat(result).isEqualTo(new Contracts.NarrationArtifact(
                "en", 2, "Hello", "https://audio.example/signed", expiresAt));
        verify(dependencies).translation("tr-en", "content-1", 2, "en", "corr-1");
        verify(dependencies).signedUrl("au-en", "corr-1");
    }

    @Test
    void getRejectsMissingLanguageOrIncompletePublishedArtifacts() {
        when(targets.findPublishedByContent("content-1", TargetStatus.PUBLISHED)).thenReturn(List.of());
        assertAppException(HttpStatus.NOT_FOUND, "NARRATION_NOT_FOUND",
                () -> service.get("content-1", "en", "corr-1"));

        JobTargetEntity incomplete = publishedTarget("j-en", 1, "en", null, "au-en");
        when(targets.findPublishedByContent("content-1", TargetStatus.PUBLISHED)).thenReturn(List.of(incomplete));
        assertAppException(HttpStatus.SERVICE_UNAVAILABLE, "NARRATION_ARTIFACT_UNAVAILABLE",
                () -> service.get("content-1", "en", "corr-1"));
        verifyNoInteractions(dependencies);
    }

    @Test
    void getRejectsTranslationFromDifferentContentVersionOrLanguage() {
        JobTargetEntity target = publishedTarget("j-en", 2, "en", "tr-en", "au-en");
        when(targets.findPublishedByContent("content-1", TargetStatus.PUBLISHED)).thenReturn(List.of(target));
        when(dependencies.translation("tr-en", "content-1", 2, "en", "corr-1"))
                .thenReturn(new Contracts.TranslationView("tr-en", "other-content", 1, "fr", "Bonjour"));
        when(dependencies.signedUrl("au-en", "corr-1"))
                .thenReturn(new Contracts.SignedUrl("https://audio.example/signed", Instant.now().plusSeconds(600)));

        assertAppException(HttpStatus.SERVICE_UNAVAILABLE, "TRANSLATION_MISMATCH",
                () -> service.get("content-1", "en", "corr-1"));
    }

    private static JobTargetEntity publishedTarget(String jobId, int version, String lang,
                                                   String translationId, String audioId) {
        Instant now = Instant.now();
        NarrationJobEntity job = new NarrationJobEntity(jobId, "content-1", version,
                "user-1", "corr-1", null, now);
        JobTargetEntity target = new JobTargetEntity("t-" + jobId, lang, "voice-1", now);
        job.addTarget(target);
        target.moveTo(TargetStatus.PUBLISHED, null, now);
        target.setTranslationIdIfAbsent(translationId);
        target.setAudioIdIfAbsent(audioId);
        return target;
    }

    private static void assertAppException(HttpStatus status, String code,
                                           org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class, ex -> {
            assertThat(ex.status()).isEqualTo(status);
            assertThat(ex.errorCode()).isEqualTo(code);
        });
    }
}
