package vn.sgu.narration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.config.RabbitConfiguration;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobDispatchServiceTest {
    private NarrationJobRepository jobs;
    private JobTargetRepository targets;
    private OutboxWriter outbox;
    private ProgressNotifications progress;
    private JobDispatchService dispatch;
    private NarrationJobEntity job;
    private JobTargetEntity english;
    private JobTargetEntity french;

    @BeforeEach
    void setUp() {
        jobs = mock(NarrationJobRepository.class);
        targets = mock(JobTargetRepository.class);
        outbox = mock(OutboxWriter.class);
        progress = mock(ProgressNotifications.class);
        dispatch = new JobDispatchService(jobs, targets, outbox, progress);
        Instant now = Instant.now();
        job = new NarrationJobEntity("j-1", "content-1", 5, "user-1", "corr-1", null, now);
        english = new JobTargetEntity("t-en", "en", "voice-en", now);
        french = new JobTargetEntity("t-fr", "fr", "voice-fr", now);
        job.addTarget(english);
        job.addTarget(french);
        when(jobs.findForUpdate("j-1")).thenReturn(Optional.of(job));
        when(targets.findAllForUpdate("j-1")).thenReturn(List.of(english, french));
    }

    @Test
    void dispatchTransitionsPendingTargetsAndWritesOutboxEvents() {
        dispatch.dispatch("j-1");

        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(english.getStatus()).isEqualTo(TargetStatus.TRANSLATING);
        assertThat(french.getStatus()).isEqualTo(TargetStatus.TRANSLATING);
        verify(outbox, times(2)).append(eq(RabbitConfiguration.EVENTS_EXCHANGE),
                eq("narration.requested"), eq("narration.requested"), eq("corr-1"),
                any(Contracts.NarrationRequested.class), any(Instant.class));
        verify(progress).publish(eq(job), anyList());
    }

    @Test
    void dispatchIsIdempotentAfterFirstCall() {
        dispatch.dispatch("j-1");
        dispatch.dispatch("j-1");

        verify(outbox, times(2)).append(anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(progress, times(1)).publish(any(), anyList());
    }

    @Test
    void missingOrNonPendingJobDoesNotDispatch() {
        when(jobs.findForUpdate("missing")).thenReturn(Optional.empty());
        dispatch.dispatch("missing");
        job.setStatus(JobStatus.CANCELLED, Instant.now());
        dispatch.dispatch("j-1");

        verifyNoInteractions(outbox, progress);
        verify(targets, never()).findAllForUpdate(anyString());
    }
}
