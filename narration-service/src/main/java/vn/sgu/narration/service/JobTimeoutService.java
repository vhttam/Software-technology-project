package vn.sgu.narration.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.IdempotencyRepository;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JobTimeoutService {
    private final NarrationJobRepository jobs;
    private final JobTargetRepository targets;
    private final IdempotencyRepository idempotency;
    private final ProgressNotifications progress;
    private final Duration translationTimeout;
    private final Duration synthesisTimeout;

    public JobTimeoutService(NarrationJobRepository jobs, JobTargetRepository targets,
                             IdempotencyRepository idempotency, ProgressNotifications progress,
                             @Value("${app.jobs.translation-timeout:PT15M}") Duration translationTimeout,
                             @Value("${app.jobs.synthesis-timeout:PT15M}") Duration synthesisTimeout) {
        this.jobs = jobs;
        this.targets = targets;
        this.idempotency = idempotency;
        this.progress = progress;
        this.translationTimeout = translationTimeout;
        this.synthesisTimeout = synthesisTimeout;
    }

    @Transactional
    public void failExpiredTargets(String jobId) {
        NarrationJobEntity job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus().isTerminal()) return;
        Instant now = Instant.now();
        List<JobTargetEntity> all = targets.findAllForUpdate(jobId);
        List<JobTargetEntity> changed = all.stream().filter(target -> {
            if (target.getStatus() == TargetStatus.TRANSLATING) {
                return target.getUpdatedAt().plus(translationTimeout).isBefore(now);
            }
            if (target.getStatus() == TargetStatus.SYNTHESIZING) {
                return target.getUpdatedAt().plus(synthesisTimeout).isBefore(now);
            }
            return false;
        }).toList();
        if (changed.isEmpty()) return;
        changed.forEach(target -> target.moveTo(TargetStatus.FAILED, "TIMEOUT", now));
        finishOrContinue(job, all, now);
        progress.publish(job, changed);
    }

    private void finishOrContinue(NarrationJobEntity job, List<JobTargetEntity> all, Instant now) {
        if (all.stream().anyMatch(target -> !target.getStatus().isTerminal())) {
            job.setStatus(JobStatus.PROCESSING, now);
            return;
        }
        boolean allPublished = all.stream().allMatch(t -> t.getStatus() == TargetStatus.PUBLISHED);
        boolean anyPublished = all.stream().anyMatch(t -> t.getStatus() == TargetStatus.PUBLISHED);
        boolean anyFailed = all.stream().anyMatch(t -> t.getStatus() == TargetStatus.FAILED);
        boolean allFailed = all.stream().allMatch(t -> t.getStatus() == TargetStatus.FAILED);
        if (allPublished) job.setStatus(JobStatus.COMPLETED, now);
        else if (anyPublished && anyFailed) job.setStatus(JobStatus.PARTIALLY_COMPLETED, now);
        else if (allFailed) job.setStatus(JobStatus.FAILED, now);
        else return;
        idempotency.findByJobId(job.getJobId()).forEach(record -> record.setExpiresAt(now.plus(Duration.ofHours(24))));
    }
}
