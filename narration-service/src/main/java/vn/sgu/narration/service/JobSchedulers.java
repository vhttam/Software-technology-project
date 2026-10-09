package vn.sgu.narration.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.sgu.narration.domain.JobStatus;
import vn.sgu.narration.repository.CancelledJobRepository;
import vn.sgu.narration.repository.IdempotencyRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Duration;
import java.time.Instant;

@Component
public class JobSchedulers {
    private final NarrationJobRepository jobs;
    private final JobDispatchService dispatcher;
    private final JobTimeoutService timeoutService;
    private final IdempotencyRepository idempotency;
    private final CancelledJobRepository cancelledJobs;
    private final Duration translationTimeout;
    private final Duration synthesisTimeout;

    public JobSchedulers(NarrationJobRepository jobs, JobDispatchService dispatcher,
                         JobTimeoutService timeoutService, IdempotencyRepository idempotency,
                         CancelledJobRepository cancelledJobs,
                         @Value("${app.jobs.translation-timeout:PT15M}") Duration translationTimeout,
                         @Value("${app.jobs.synthesis-timeout:PT15M}") Duration synthesisTimeout) {
        this.jobs = jobs;
        this.dispatcher = dispatcher;
        this.timeoutService = timeoutService;
        this.idempotency = idempotency;
        this.cancelledJobs = cancelledJobs;
        this.translationTimeout = translationTimeout;
        this.synthesisTimeout = synthesisTimeout;
    }

    @Scheduled(fixedDelayString = "${app.jobs.dispatch-delay:PT2S}")
    public void dispatchPendingJobs() {
        jobs.findTop100ByStatusOrderByCreatedAtAsc(JobStatus.PENDING)
                .forEach(job -> dispatcher.dispatch(job.getJobId()));
    }

    @Scheduled(fixedDelayString = "${app.jobs.timeout-scan-delay:PT30S}")
    public void timeoutJobs() {
        Instant now = Instant.now();
        jobs.findTimedOutJobIds(JobStatus.PROCESSING, now.minus(translationTimeout),
                        now.minus(synthesisTimeout), PageRequest.of(0, 100))
                .forEach(timeoutService::failExpiredTargets);
    }

    @Scheduled(fixedDelayString = "PT1H")
    public void cleanExpiredRows() {
        Instant now = Instant.now();
        idempotency.deleteExpiredForTerminalJobs(now);
        cancelledJobs.deleteByExpiresAtBefore(now);
    }
}
