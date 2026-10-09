package vn.sgu.narration.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sgu.narration.api.AppException;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.IdempotencyRepository;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class JobEventProcessor {
    private final NarrationJobRepository jobs;
    private final JobTargetRepository targets;
    private final IdempotencyRepository idempotency;
    private final ProgressNotifications progress;

    public JobEventProcessor(NarrationJobRepository jobs, JobTargetRepository targets,
                             IdempotencyRepository idempotency, ProgressNotifications progress) {
        this.jobs = jobs;
        this.targets = targets;
        this.idempotency = idempotency;
        this.progress = progress;
    }

    @Transactional
    public void translationCompleted(Contracts.TranslationCompleted event) {
        if (event == null) throw invalid("Event translation.completed rỗng");
        validateTargetEvent(event.jobId(), event.targetId(), event.lang());
        requireText(event.translationId(), "translationId");
        NarrationJobEntity job = lockJob(event.jobId());
        JobTargetEntity target = lockTarget(job, event.targetId(), event.lang());
        if (ignoreLate(job, target)) return;
        Instant now = Instant.now();
        if (target.getStatus() == TargetStatus.PENDING) {
            throw invalid("translation.completed đến trước khi target được dispatch");
        }
        if (target.getStatus() == TargetStatus.SYNTHESIZING) {
            if (event.translationId().equals(target.getTranslationId())) return;
            throw invalid("translation.completed lặp lại với translationId khác");
        }
        target.setTranslationIdIfAbsent(event.translationId());
        if (target.getStatus().rank() < TargetStatus.SYNTHESIZING.rank()) {
            target.moveTo(TargetStatus.SYNTHESIZING, null, now);
        }
        if (job.getStatus() == JobStatus.PENDING) job.setStatus(JobStatus.PROCESSING, now);
        finishOrContinue(job, targets.findAllForUpdate(job.getJobId()), now);
        progress.publish(job, List.of(target));
    }

    @Transactional
    public void translationFailed(Contracts.TranslationFailed event) {
        if (event == null) throw invalid("Event translation.failed rỗng");
        validateTargetEvent(event.jobId(), event.targetId(), event.lang());
        normalizedError(event.errorCode());
        NarrationJobEntity job = lockJob(event.jobId());
        JobTargetEntity target = lockTarget(job, event.targetId(), event.lang());
        if (ignoreLate(job, target)) return;
        Instant now = Instant.now();
        if (target.getStatus() == TargetStatus.PENDING) {
            throw invalid("translation.failed đến trước khi target được dispatch");
        }
        target.moveTo(TargetStatus.FAILED, normalizedError(event.errorCode()), now);
        finishOrContinue(job, targets.findAllForUpdate(job.getJobId()), now);
        progress.publish(job, List.of(target));
    }

    @Transactional
    public void ttsCompleted(Contracts.TtsCompleted event) {
        if (event == null) throw invalid("Event tts.completed rỗng");
        validateTargetEvent(event.jobId(), event.targetId(), event.lang());
        requireText(event.translationId(), "translationId");
        requireText(event.audioId(), "audioId");
        NarrationJobEntity job = lockJob(event.jobId());
        JobTargetEntity target = lockTarget(job, event.targetId(), event.lang());
        if (ignoreLate(job, target)) return;
        Instant now = Instant.now();
        if (target.getStatus() == TargetStatus.PENDING) {
            throw invalid("tts.completed đến trước khi target được dispatch");
        }
        if (target.getTranslationId() != null && !target.getTranslationId().equals(event.translationId())) {
            throw invalid("tts.completed có translationId không khớp target");
        }
        target.setTranslationIdIfAbsent(event.translationId());
        target.setAudioIdIfAbsent(event.audioId());
        target.moveTo(TargetStatus.PUBLISHED, null, now);
        if (job.getStatus() == JobStatus.PENDING) job.setStatus(JobStatus.PROCESSING, now);
        finishOrContinue(job, targets.findAllForUpdate(job.getJobId()), now);
        progress.publish(job, List.of(target));
    }

    @Transactional
    public void ttsFailed(Contracts.TtsFailed event) {
        if (event == null) throw invalid("Event tts.failed rỗng");
        validateTargetEvent(event.jobId(), event.targetId(), event.lang());
        normalizedError(event.errorCode());
        NarrationJobEntity job = lockJob(event.jobId());
        JobTargetEntity target = lockTarget(job, event.targetId(), event.lang());
        if (ignoreLate(job, target)) return;
        Instant now = Instant.now();
        if (target.getStatus() == TargetStatus.PENDING) {
            throw invalid("tts.failed đến trước khi target được dispatch");
        }
        target.moveTo(TargetStatus.FAILED, normalizedError(event.errorCode()), now);
        finishOrContinue(job, targets.findAllForUpdate(job.getJobId()), now);
        progress.publish(job, List.of(target));
    }

    private NarrationJobEntity lockJob(String jobId) {
        return jobs.findForUpdate(jobId).orElseThrow(() -> invalid("jobId không tồn tại"));
    }

    private JobTargetEntity lockTarget(NarrationJobEntity job, String targetId, String lang) {
        JobTargetEntity target = targets.findForUpdate(job.getJobId(), targetId)
                .orElseThrow(() -> invalid("targetId không thuộc job"));
        if (lang == null || !target.getLang().equalsIgnoreCase(lang)) {
            throw invalid("lang không khớp targetId");
        }
        return target;
    }

    private boolean ignoreLate(NarrationJobEntity job, JobTargetEntity target) {
        return job.getStatus() == JobStatus.CANCELLED || job.getStatus().isTerminal() || target.getStatus().isTerminal();
    }

    private String normalizedError(String errorCode) {
        if (errorCode == null || !List.of("TIMEOUT", "PROVIDER_ERROR", "CONTENT_NOT_FOUND", "STORAGE_ERROR", "INTERNAL_ERROR")
                .contains(errorCode)) {
            throw invalid("errorCode không thuộc enum của hợp đồng");
        }
        return errorCode;
    }

    private void finishOrContinue(NarrationJobEntity job, List<JobTargetEntity> allTargets, Instant now) {
        if (job.getStatus() == JobStatus.CANCELLED || job.getStatus().isTerminal()) return;
        if (allTargets.stream().anyMatch(target -> !target.getStatus().isTerminal())) {
            job.setStatus(JobStatus.PROCESSING, now);
            return;
        }
        boolean allPublished = allTargets.stream().allMatch(target -> target.getStatus() == TargetStatus.PUBLISHED);
        boolean anyPublished = allTargets.stream().anyMatch(target -> target.getStatus() == TargetStatus.PUBLISHED);
        boolean allFailed = allTargets.stream().allMatch(target -> target.getStatus() == TargetStatus.FAILED);
        if (allPublished) job.setStatus(JobStatus.COMPLETED, now);
        else if (anyPublished && allTargets.stream().anyMatch(target -> target.getStatus() == TargetStatus.FAILED)) {
            job.setStatus(JobStatus.PARTIALLY_COMPLETED, now);
        } else if (allFailed) job.setStatus(JobStatus.FAILED, now);
        else throw invalid("Trạng thái target cuối không thể tổng hợp thành trạng thái job hợp lệ");
        idempotency.findByJobId(job.getJobId()).forEach(record -> record.setExpiresAt(now.plus(Duration.ofHours(24))));
    }

    private AppException invalid(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "INVALID_EVENT", message);
    }

    private void validateTargetEvent(String jobId, String targetId, String lang) {
        requireText(jobId, "jobId");
        requireText(targetId, "targetId");
        requireText(lang, "lang");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " bị thiếu hoặc rỗng");
    }
}
