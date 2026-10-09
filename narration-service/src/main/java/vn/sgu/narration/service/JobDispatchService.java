package vn.sgu.narration.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.config.RabbitConfiguration;
import vn.sgu.narration.domain.*;
import vn.sgu.narration.repository.JobTargetRepository;
import vn.sgu.narration.repository.NarrationJobRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class JobDispatchService {
    private final NarrationJobRepository jobs;
    private final JobTargetRepository targets;
    private final OutboxWriter outbox;
    private final ProgressNotifications progress;

    public JobDispatchService(NarrationJobRepository jobs, JobTargetRepository targets,
                              OutboxWriter outbox, ProgressNotifications progress) {
        this.jobs = jobs;
        this.targets = targets;
        this.outbox = outbox;
        this.progress = progress;
    }

    @Transactional
    public void dispatch(String jobId) {
        NarrationJobEntity job = jobs.findForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != JobStatus.PENDING) return;
        Instant now = Instant.now();
        job.setStatus(JobStatus.PROCESSING, now);
        List<JobTargetEntity> all = targets.findAllForUpdate(jobId);
        List<JobTargetEntity> changed = new ArrayList<>();
        for (JobTargetEntity target : all) {
            if (target.getStatus() != TargetStatus.PENDING) continue;
            target.moveTo(TargetStatus.TRANSLATING, null, now);
            outbox.append(RabbitConfiguration.EVENTS_EXCHANGE, "narration.requested", "narration.requested",
                    job.getCorrelationId(), new Contracts.NarrationRequested(job.getJobId(), target.getTargetId(),
                            job.getContentId(), job.getContentVersion(), target.getLang(), target.getVoiceId()), now);
            changed.add(target);
        }
        job.touch(now);
        if (!changed.isEmpty()) progress.publish(job, changed);
    }
}
