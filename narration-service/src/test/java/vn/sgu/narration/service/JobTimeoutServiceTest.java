package vn.sgu.narration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.IdempotencyRepository;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JobTimeoutServiceTest {
    private NarrationJobRepository jobs;
    private JobTargetRepository targets;
    private IdempotencyRepository idempotency;
    private ProgressNotifications progress;
    private JobTimeoutService timeouts;

    @BeforeEach
    void setUp() {
        jobs = mock(NarrationJobRepository.class);
        targets = mock(JobTargetRepository.class);
        idempotency = mock(IdempotencyRepository.class);
        progress = mock(ProgressNotifications.class);
        timeouts = new JobTimeoutService(jobs, targets, idempotency, progress,
                Duration.ofMinutes(15), Duration.ofMinutes(10));
    }

    @Test
    void expiredTranslationTargetFailsAndFinishesJob() {
        Fixture f = fixture(TargetStatus.TRANSLATING, Instant.now().minus(Duration.ofMinutes(16)));
        when(idempotency.findByJobId("j-1")).thenReturn(List.of());

        timeouts.failExpiredTargets("j-1");

        assertThat(f.target.getStatus()).isEqualTo(TargetStatus.FAILED);
        assertThat(f.target.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(f.job.getStatus()).isEqualTo(JobStatus.FAILED);
        verify(progress).publish(f.job, List.of(f.target));
    }

    @Test
    void expiredSynthesisAlongsidePublishedTargetFinishesPartially() {
        Fixture f = fixture(TargetStatus.SYNTHESIZING, Instant.now().minus(Duration.ofMinutes(11)));
        JobTargetEntity published = new JobTargetEntity("t-fr", "fr", "voice-fr", Instant.now());
        f.job.addTarget(published);
        published.moveTo(TargetStatus.PUBLISHED, null, Instant.now());
        when(targets.findAllForUpdate("j-1")).thenAnswer(ignored -> f.job.getTargets());
        when(idempotency.findByJobId("j-1")).thenReturn(List.of());

        timeouts.failExpiredTargets("j-1");

        assertThat(f.target.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(f.job.getStatus()).isEqualTo(JobStatus.PARTIALLY_COMPLETED);
        verify(progress).publish(f.job, List.of(f.target));
    }

    @Test
    void freshTargetAndMissingOrTerminalJobAreNoOps() {
        Fixture f = fixture(TargetStatus.TRANSLATING, Instant.now());
        timeouts.failExpiredTargets("j-1");
        assertThat(f.target.getStatus()).isEqualTo(TargetStatus.TRANSLATING);
        verify(progress, never()).publish(any(), anyList());

        when(jobs.findForUpdate("missing")).thenReturn(Optional.empty());
        timeouts.failExpiredTargets("missing");
        f.job.setStatus(JobStatus.COMPLETED, Instant.now());
        timeouts.failExpiredTargets("j-1");

        verify(targets, times(1)).findAllForUpdate("j-1");
    }

    private Fixture fixture(TargetStatus status, Instant updatedAt) {
        NarrationJobEntity job = new NarrationJobEntity("j-1", "content-1", 2,
                "user-1", "corr-1", null, updatedAt);
        JobTargetEntity target = new JobTargetEntity("t-en", "en", "voice-en", updatedAt);
        job.addTarget(target);
        target.moveTo(status, null, updatedAt);
        when(jobs.findForUpdate("j-1")).thenReturn(Optional.of(job));
        when(targets.findAllForUpdate("j-1")).thenReturn(List.of(target));
        return new Fixture(job, target);
    }

    private record Fixture(NarrationJobEntity job, JobTargetEntity target) {}
}
