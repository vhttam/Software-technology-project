package vn.sgu.narration.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.ObjectMapper;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.integration.NarrationDependencies;
import vn.sgu.narration.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobApplicationServiceTest {
    private NarrationJobRepository jobs;
    private JobTargetRepository targets;
    private IdempotencyRepository idempotency;
    private CancelledJobRepository cancelledJobs;
    private JdbcTemplate jdbc;
    private NarrationDependencies dependencies;
    private OutboxWriter outbox;
    private ProgressNotifications progress;
    private PlatformTransactionManager transactionManager;
    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        jobs = mock(NarrationJobRepository.class);
        targets = mock(JobTargetRepository.class);
        idempotency = mock(IdempotencyRepository.class);
        cancelledJobs = mock(CancelledJobRepository.class);
        jdbc = mock(JdbcTemplate.class);
        dependencies = mock(NarrationDependencies.class);
        outbox = mock(OutboxWriter.class);
        progress = mock(ProgressNotifications.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        when(idempotency.findByUserIdAndOperationAndIdempotencyKey(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(jobs.findByContentVersionAndStatuses(anyString(), anyInt(), anyList())).thenReturn(List.of());
        when(jobs.saveAndFlush(any(NarrationJobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dependencies.validateContentForJob(anyString(), anyString()))
                .thenAnswer(invocation -> new Contracts.ContentCheck(invocation.getArgument(0), 3, "vi"));
        when(dependencies.voices(anyString())).thenReturn(new Contracts.VoiceCatalog(List.of(
                new Contracts.VoiceGroup("en", List.of("en-voice")),
                new Contracts.VoiceGroup("fr", List.of("fr-voice")))));
        service = new JobApplicationService(jobs, targets, idempotency, cancelledJobs, jdbc,
                dependencies, new JobViewMapper(), outbox, progress, new ObjectMapper(),
                transactionManager, java.time.Duration.ofHours(48));
    }

    @Test
    void createsJobAndTargetsForValidRequest() {
        Contracts.JobView result = service.create("user-1", "corr-1", "idem-1", request("content-1",
                new Contracts.TargetInput("fr", "fr-voice"),
                new Contracts.TargetInput("en", "en-voice")));

        assertThat(result.jobId()).startsWith("j-");
        assertThat(result.contentId()).isEqualTo("content-1");
        assertThat(result.version()).isEqualTo(3);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.targets()).extracting(Contracts.TargetView::lang).containsExactly("en", "fr");
        verify(dependencies).validateContentForJob("content-1", "corr-1");
        verify(idempotency).save(any(IdempotencyEntity.class));
    }

    @Test
    void rejectsMissingOrInvalidIdempotencyUserAndTargetsBeforeCallingDependencies() {
        assertAppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED",
                () -> service.create("user-1", "corr-1", " ", request("content-1", enTarget())));
        assertAppException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                () -> service.create("user-1", "corr-1", "k".repeat(256), request("content-1", enTarget())));
        assertAppException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED_USER",
                () -> service.create(" ", "corr-1", "idem-1", request("content-1", enTarget())));
        assertAppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS",
                () -> service.create("user-1", "corr-1", "idem-1",
                        new Contracts.CreateJobRequest("content-1", null, List.of())));
        assertAppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS",
                () -> service.create("user-1", "corr-1", "idem-1", request("content-1",
                        enTarget(), new Contracts.TargetInput("EN", "other-voice"))));
        verifyNoInteractions(dependencies);
    }

    @Test
    void rejectsUnavailableDeletedOrMissingContentAndUnsupportedVoice() {
        when(dependencies.validateContentForJob("missing-content", "corr-1"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Content missing"));
        assertAppException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", () ->
                service.create("user-1", "corr-1", "idem-1", request("missing-content", enTarget())));

        when(dependencies.validateContentForJob("deleted-content", "corr-1"))
                .thenThrow(new AppException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Content deleted"));
        assertAppException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", () ->
                service.create("user-1", "corr-1", "idem-2", request("deleted-content", enTarget())));

        assertAppException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS", () ->
                service.create("user-1", "corr-1", "idem-3", request("content-1",
                        new Contracts.TargetInput("en", "not-supported"))));
        verify(jobs, never()).saveAndFlush(any());
    }

    @Test
    void dependencyFailuresStopCreationBeforePersistingJob() {
        when(dependencies.validateContentForJob("content-down", "corr-1"))
                .thenThrow(new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                        "CONTENT_SERVICE_UNAVAILABLE", "Content Service unavailable"));
        assertAppException(HttpStatus.SERVICE_UNAVAILABLE, "CONTENT_SERVICE_UNAVAILABLE", () ->
                service.create("user-1", "corr-1", "idem-content-down", request("content-down", enTarget())));

        when(dependencies.voices("corr-2")).thenReturn(null);
        assertAppException(HttpStatus.SERVICE_UNAVAILABLE, "TTS_SERVICE_UNAVAILABLE", () ->
                service.create("user-1", "corr-2", "idem-tts-down", request("content-1", enTarget())));

        verify(jobs, never()).saveAndFlush(any());
        verify(idempotency, never()).save(any());
    }

    @Test
    void idempotentRepeatReturnsOriginalJobAndDifferentPayloadConflicts() {
        AtomicReference<NarrationJobEntity> savedJob = new AtomicReference<>();
        when(jobs.saveAndFlush(any(NarrationJobEntity.class))).thenAnswer(invocation -> {
            NarrationJobEntity job = invocation.getArgument(0);
            savedJob.set(job);
            return job;
        });
        Contracts.CreateJobRequest original = request("content-1", enTarget());
        Contracts.JobView created = service.create("user-1", "corr-1", "idem-1", original);
        var captor = org.mockito.ArgumentCaptor.forClass(IdempotencyEntity.class);
        verify(idempotency).save(captor.capture());
        IdempotencyEntity record = captor.getValue();
        when(idempotency.findByUserIdAndOperationAndIdempotencyKey("user-1", "POST:/jobs", "idem-1"))
                .thenReturn(Optional.of(record));
        when(jobs.findById(created.jobId())).thenReturn(Optional.of(savedJob.get()));

        Contracts.JobView repeated = service.create("user-1", "corr-2", "idem-1", original);
        assertThat(repeated.jobId()).isEqualTo(created.jobId());

        assertAppException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", () ->
                service.create("user-1", "corr-2", "idem-1", request("content-2", enTarget())));
    }

    @Test
    void rejectsConflictingActiveJobAndUnavailableApplicationLock() {
        NarrationJobEntity active = new NarrationJobEntity("j-active", "content-1", 3,
                "user-2", "corr-2", null, Instant.now());
        active.setStatus(JobStatus.PROCESSING, Instant.now());
        active.addTarget(new JobTargetEntity("t-en", "en", "en-voice", Instant.now()));
        when(jobs.findByContentVersionAndStatuses(anyString(), anyInt(), anyList())).thenReturn(List.of(active));
        assertAppException(HttpStatus.CONFLICT, "ACTIVE_JOB_EXISTS", () ->
                service.create("user-1", "corr-1", "idem-1", request("content-1", enTarget())));

        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(-1);
        assertAppException(HttpStatus.SERVICE_UNAVAILABLE, "IDEMPOTENCY_LOCK_UNAVAILABLE", () ->
                service.create("user-1", "corr-1", "idem-2", request("content-1", enTarget())));
    }

    @Test
    void retryReferenceAllowsMatchingActiveJobAndRejectsStaleVersion() {
        NarrationJobEntity active = new NarrationJobEntity("j-active", "content-1", 3,
                "user-2", "corr-2", null, Instant.now());
        active.setStatus(JobStatus.PROCESSING, Instant.now());
        active.addTarget(new JobTargetEntity("t-en", "en", "en-voice", Instant.now()));
        NarrationJobEntity activeOther = new NarrationJobEntity("j-active-other", "content-1", 3,
                "user-3", "corr-3", null, Instant.now());
        activeOther.setStatus(JobStatus.PROCESSING, Instant.now());
        activeOther.addTarget(new JobTargetEntity("t-en-other", "en", "en-voice", Instant.now()));
        when(jobs.findByContentVersionAndStatuses(anyString(), anyInt(), anyList()))
                .thenReturn(List.of(active, activeOther));
        when(jobs.findById("j-active")).thenReturn(Optional.of(active));
        Contracts.CreateJobRequest retry = new Contracts.CreateJobRequest("content-1", "j-active", List.of(enTarget()));

        Contracts.JobView created = service.create("user-1", "corr-1", "idem-retry", retry);

        assertThat(created.status()).isEqualTo("PENDING");
        verify(jobs).saveAndFlush(argThat(job -> "j-active".equals(job.getRetryOfJobId())));

        NarrationJobEntity stale = new NarrationJobEntity("j-old", "content-1", 2,
                "user-2", "corr-2", null, Instant.now());
        stale.setStatus(JobStatus.PROCESSING, Instant.now());
        stale.addTarget(new JobTargetEntity("t-old-en", "en", "en-voice", Instant.now()));
        when(jobs.findByContentVersionAndStatuses(anyString(), anyInt(), anyList())).thenReturn(List.of());
        when(jobs.findById("j-old")).thenReturn(Optional.of(stale));
        Contracts.CreateJobRequest staleRetry = new Contracts.CreateJobRequest("content-1", "j-old", List.of(enTarget()));

        assertAppException(HttpStatus.CONFLICT, "CONTENT_VERSION_CHANGED", () ->
                service.create("user-1", "corr-1", "idem-stale", staleRetry));
    }

    @Test
    void expiredIdempotencyKeyRemainsBoundWhileJobIsActiveThenCanBeReusedAfterTerminalState() {
        AtomicReference<NarrationJobEntity> savedJob = new AtomicReference<>();
        when(jobs.saveAndFlush(any(NarrationJobEntity.class))).thenAnswer(invocation -> {
            NarrationJobEntity job = invocation.getArgument(0);
            savedJob.set(job);
            return job;
        });
        Contracts.CreateJobRequest request = request("content-1", enTarget());
        Contracts.JobView first = service.create("user-1", "corr-1", "idem-expiring", request);
        var captor = org.mockito.ArgumentCaptor.forClass(IdempotencyEntity.class);
        verify(idempotency).save(captor.capture());
        IdempotencyEntity record = captor.getValue();
        record.setExpiresAt(Instant.now().minusSeconds(1));
        when(idempotency.findByUserIdAndOperationAndIdempotencyKey("user-1", "POST:/jobs", "idem-expiring"))
                .thenReturn(Optional.of(record));
        when(jobs.findById(first.jobId())).thenReturn(Optional.of(savedJob.get()));

        Contracts.JobView stillActive = service.create("user-1", "corr-2", "idem-expiring", request);
        assertThat(stillActive.jobId()).isEqualTo(first.jobId());
        verify(dependencies, times(1)).validateContentForJob("content-1", "corr-1");

        savedJob.get().setStatus(JobStatus.COMPLETED, Instant.now());
        when(idempotency.findByUserIdAndOperationAndIdempotencyKey("user-1", "POST:/jobs", "idem-expiring"))
                .thenReturn(Optional.of(record), Optional.of(record));
        Contracts.JobView recreated = service.create("user-1", "corr-3", "idem-expiring", request);

        assertThat(recreated.jobId()).isNotEqualTo(first.jobId());
        verify(idempotency).delete(record);
    }

    @Test
    void cancellationMarksTargetsCancelledPersistsRetentionAndWritesOutbox() {
        Instant now = Instant.now();
        NarrationJobEntity job = new NarrationJobEntity("j-1", "content-1", 3,
                "user-1", "corr-1", null, now);
        job.setStatus(JobStatus.PROCESSING, now);
        JobTargetEntity published = new JobTargetEntity("t-en", "en", "en-voice", now);
        JobTargetEntity failed = new JobTargetEntity("t-fr", "fr", "fr-voice", now);
        JobTargetEntity pending = new JobTargetEntity("t-vi", "vi", "vi-voice", now);
        job.addTarget(published);
        job.addTarget(failed);
        job.addTarget(pending);
        published.moveTo(TargetStatus.PUBLISHED, null, now);
        failed.moveTo(TargetStatus.FAILED, "PROVIDER_ERROR", now);
        IdempotencyEntity record = new IdempotencyEntity("user-1", "POST:/jobs", "idem-1", "hash",
                "j-1", now.plusSeconds(60), now);
        when(jobs.findForUpdate("j-1")).thenReturn(Optional.of(job));
        when(targets.findAllForUpdate("j-1")).thenReturn(List.of(published, failed, pending));
        when(idempotency.findByJobId("j-1")).thenReturn(List.of(record));

        Contracts.JobView result = service.cancel("j-1");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.targets().get(0).status()).isEqualTo("CANCELLED");
        assertThat(result.targets()).extracting(Contracts.TargetView::status)
                .containsExactly("CANCELLED", "FAILED", "CANCELLED");
        assertThat(record.getExpiresAt()).isAfter(Instant.now().plusSeconds(23 * 60 * 60));
        verify(cancelledJobs).save(argThat(row -> ReflectionTestUtils.getField(row, "jobId").equals("j-1")
                && ((Instant) ReflectionTestUtils.getField(row, "expiresAt"))
                .isAfter((Instant) ReflectionTestUtils.getField(row, "cancelledAt"))));
        verify(outbox).append(anyString(), anyString(), eq("narration.cancelled"), eq("corr-1"),
                any(Contracts.NarrationCancelled.class), any(Instant.class));
        verify(progress).publish(eq(job), anyList());
    }

    @Test
    void cancellationIsAllowedWhileJobIsStillPending() {
        Instant now = Instant.now();
        NarrationJobEntity job = new NarrationJobEntity("j-pending", "content-1", 3,
                "user-1", "corr-1", null, now);
        JobTargetEntity target = new JobTargetEntity("t-en", "en", "en-voice", now);
        job.addTarget(target);
        when(jobs.findForUpdate("j-pending")).thenReturn(Optional.of(job));
        when(targets.findAllForUpdate("j-pending")).thenReturn(List.of(target));
        when(idempotency.findByJobId("j-pending")).thenReturn(List.of());

        Contracts.JobView result = service.cancel("j-pending");

        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.targets()).extracting(Contracts.TargetView::status).containsExactly("CANCELLED");
        verify(outbox).append(anyString(), anyString(), eq("narration.cancelled"), eq("corr-1"),
                any(Contracts.NarrationCancelled.class), any(Instant.class));
    }

    @Test
    void cancellationOfMissingOrTerminalJobReturnsConflictOrNotFound() {
        when(jobs.findForUpdate("missing")).thenReturn(Optional.empty());
        assertAppException(HttpStatus.NOT_FOUND, "JOB_NOT_FOUND", () -> service.cancel("missing"));

        NarrationJobEntity completed = new NarrationJobEntity("j-done", "content-1", 3,
                "user-1", "corr-1", null, Instant.now());
        completed.setStatus(JobStatus.COMPLETED, Instant.now());
        when(jobs.findForUpdate("j-done")).thenReturn(Optional.of(completed));
        assertAppException(HttpStatus.CONFLICT, "JOB_ALREADY_FINISHED", () -> service.cancel("j-done"));
        verifyNoInteractions(outbox, progress);
    }

    private static Contracts.CreateJobRequest request(String contentId, Contracts.TargetInput... targets) {
        return new Contracts.CreateJobRequest(contentId, null, List.of(targets));
    }

    private static Contracts.TargetInput enTarget() {
        return new Contracts.TargetInput("en", "en-voice");
    }

    private static void assertAppException(HttpStatus status, String code,
                                           org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AppException.class, ex -> {
            assertThat(ex.status()).isEqualTo(status);
            assertThat(ex.errorCode()).isEqualTo(code);
        });
    }
}
