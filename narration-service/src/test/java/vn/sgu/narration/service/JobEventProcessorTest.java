package vn.sgu.narration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.IdempotencyRepository;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobEventProcessorTest {
    private NarrationJobRepository jobs;
    private JobTargetRepository targets;
    private IdempotencyRepository idempotency;
    private ProgressNotifications progress;
    private JobEventProcessor processor;
    private NarrationJobEntity job;
    private JobTargetEntity english;

    @BeforeEach
    void setUp() {
        jobs = mock(NarrationJobRepository.class);
        targets = mock(JobTargetRepository.class);
        idempotency = mock(IdempotencyRepository.class);
        progress = mock(ProgressNotifications.class);
        processor = new JobEventProcessor(jobs, targets, idempotency, progress);
        Instant now = Instant.now().minusSeconds(60);
        job = new NarrationJobEntity("j-1", "content-1", 3, "user-1", "corr-1", null, now);
        english = new JobTargetEntity("t-en", "en", "voice-en", now);
        job.addTarget(english);
        english.moveTo(TargetStatus.TRANSLATING, null, now);
        when(jobs.findForUpdate("j-1")).thenReturn(Optional.of(job));
        when(targets.findForUpdate("j-1", "t-en")).thenReturn(Optional.of(english));
        when(targets.findAllForUpdate("j-1")).thenAnswer(ignored -> job.getTargets());
        when(idempotency.findByJobId("j-1")).thenReturn(List.of());
    }

    @Test
    void translationAndTtsCompletionPublishTargetAndCompleteJob() {
        processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-1", "en", "voice-en"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.SYNTHESIZING);
        assertThat(english.getTranslationId()).isEqualTo("tr-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);

        processor.ttsCompleted(new Contracts.TtsCompleted("j-1", "t-en", "tr-1", "audio-1", "en"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.PUBLISHED);
        assertThat(english.getAudioId()).isEqualTo("audio-1");
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getFinishedAt()).isNotNull();
        verify(progress, times(2)).publish(eq(job), anyList());
    }

    @Test
    void ttsCompletionCanArriveBeforeTranslationCompletionAndLateTranslationIsIgnored() {
        processor.ttsCompleted(new Contracts.TtsCompleted("j-1", "t-en", "tr-1", "audio-1", "en"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.PUBLISHED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);

        processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-1", "en", "voice-en"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.PUBLISHED);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        verify(progress, times(1)).publish(eq(job), anyList());
    }

    @Test
    void translationFailureMakesSingleTargetJobFailed() {
        Instant now = Instant.now();
        IdempotencyEntity record = new IdempotencyEntity("user-1", "POST:/jobs", "idem-1", "hash",
                "j-1", now.plusSeconds(60), now);
        when(idempotency.findByJobId("j-1")).thenReturn(List.of(record));

        processor.translationFailed(new Contracts.TranslationFailed("j-1", "t-en", "en", "PROVIDER_ERROR"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.FAILED);
        assertThat(english.getErrorCode()).isEqualTo("PROVIDER_ERROR");
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(record.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 60 * 60));
    }

    @Test
    void publishedAndFailedTargetsMakeJobPartiallyCompleted() {
        JobTargetEntity french = new JobTargetEntity("t-fr", "fr", "voice-fr", Instant.now());
        job.addTarget(french);
        french.moveTo(TargetStatus.PUBLISHED, null, Instant.now());
        when(targets.findAllForUpdate("j-1")).thenAnswer(ignored -> job.getTargets());

        processor.ttsFailed(new Contracts.TtsFailed("j-1", "t-en", "en", "TIMEOUT"));

        assertThat(job.getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        assertThat(french.getStatus()).isEqualTo(TargetStatus.PUBLISHED);
        assertThat(english.getErrorCode()).isEqualTo("TIMEOUT");
    }

    @Test
    void lateEventForTerminalTargetIsIgnored() {
        english.moveTo(TargetStatus.FAILED, "TIMEOUT", Instant.now());

        processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-late", "en", "voice-en"));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.FAILED);
        verifyNoInteractions(progress);
    }

    @Test
    void rejectsTranslationCompletionBeforeDispatch() {
        NarrationJobEntity pendingJob = new NarrationJobEntity("j-pending", "content-1", 3,
                "user-1", "corr-1", null, Instant.now());
        JobTargetEntity pendingTarget = new JobTargetEntity("t-pending", "en", "voice-en", Instant.now());
        pendingJob.addTarget(pendingTarget);
        when(jobs.findForUpdate("j-pending")).thenReturn(Optional.of(pendingJob));
        when(targets.findForUpdate("j-pending", "t-pending")).thenReturn(Optional.of(pendingTarget));

        assertThatThrownBy(() -> processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-pending", "t-pending", "tr-1", "en", "voice-en")))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_EVENT");
    }

    @Test
    void rejectsAnyResultEventBeforeDispatchWithoutChangingPendingTarget() {
        NarrationJobEntity pendingJob = new NarrationJobEntity("j-pending", "content-1", 3,
                "user-1", "corr-1", null, Instant.now());
        JobTargetEntity pendingTarget = new JobTargetEntity("t-pending", "en", "voice-en", Instant.now());
        pendingJob.addTarget(pendingTarget);
        when(jobs.findForUpdate("j-pending")).thenReturn(Optional.of(pendingJob));
        when(targets.findForUpdate("j-pending", "t-pending")).thenReturn(Optional.of(pendingTarget));

        assertInvalid(() -> processor.translationFailed(new Contracts.TranslationFailed(
                "j-pending", "t-pending", "en", "PROVIDER_ERROR")));
        assertInvalid(() -> processor.ttsCompleted(new Contracts.TtsCompleted(
                "j-pending", "t-pending", "tr-1", "audio-1", "en")));
        assertInvalid(() -> processor.ttsFailed(new Contracts.TtsFailed(
                "j-pending", "t-pending", "en", "STORAGE_ERROR")));

        assertThat(pendingTarget.getStatus()).isEqualTo(TargetStatus.PENDING);
        assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.PENDING);
        verifyNoInteractions(progress);
    }

    @Test
    void repeatedTranslationCompletionIsIdempotentAndConflictingTranslationIsRejected() {
        Contracts.TranslationCompleted completed = new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-1", "en", "voice-en");
        processor.translationCompleted(completed);
        processor.translationCompleted(completed);

        assertThat(english.getStatus()).isEqualTo(TargetStatus.SYNTHESIZING);
        assertThat(english.getTranslationId()).isEqualTo("tr-1");
        verify(progress, times(1)).publish(eq(job), anyList());

        assertInvalid(() -> processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-conflict", "en", "voice-en")));
        assertThat(english.getTranslationId()).isEqualTo("tr-1");
        verify(progress, times(1)).publish(eq(job), anyList());
    }

    @Test
    void rejectsTtsCompletionWithTranslationIdConflictingWithEarlierTranslationEvent() {
        processor.translationCompleted(new Contracts.TranslationCompleted(
                "j-1", "t-en", "tr-1", "en", "voice-en"));

        assertInvalid(() -> processor.ttsCompleted(new Contracts.TtsCompleted(
                "j-1", "t-en", "tr-other", "audio-1", "en")));

        assertThat(english.getStatus()).isEqualTo(TargetStatus.SYNTHESIZING);
        assertThat(english.getAudioId()).isNull();
    }

    @Test
    void rejectsNullEventMissingFieldsUnknownErrorAndMismatchedTarget() {
        assertInvalid(() -> processor.translationCompleted(null));
        assertInvalid(() -> processor.translationCompleted(
                new Contracts.TranslationCompleted("j-1", "t-en", " ", "en", "voice-en")));
        assertInvalid(() -> processor.translationFailed(
                new Contracts.TranslationFailed("j-1", "t-en", "en", "NOT_IN_CONTRACT")));
        assertInvalid(() -> processor.ttsCompleted(
                new Contracts.TtsCompleted("j-1", "t-en", null, "audio-1", "en")));
        assertInvalid(() -> processor.ttsFailed(
                new Contracts.TtsFailed("j-1", "t-en", "fr", "TIMEOUT")));
    }

    @Test
    void rejectsEventForUnknownJobOrTarget() {
        when(jobs.findForUpdate("missing")).thenReturn(Optional.empty());
        assertInvalid(() -> processor.translationFailed(
                new Contracts.TranslationFailed("missing", "t-en", "en", "TIMEOUT")));

        when(targets.findForUpdate("j-1", "missing-target")).thenReturn(Optional.empty());
        assertInvalid(() -> processor.translationFailed(
                new Contracts.TranslationFailed("j-1", "missing-target", "en", "TIMEOUT")));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INVALID_EVENT");
    }
}
