package vn.sgu.narration.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.domain.JobTargetEntity;
import vn.sgu.narration.domain.NarrationJobEntity;

import java.time.Instant;
import java.util.List;

@Component
public class ProgressNotifications {
    private final ApplicationEventPublisher publisher;
    private final JobViewMapper mapper;

    public ProgressNotifications(ApplicationEventPublisher publisher, JobViewMapper mapper) {
        this.publisher = publisher;
        this.mapper = mapper;
    }

    public void publish(NarrationJobEntity job, List<JobTargetEntity> changedTargets) {
        List<Contracts.ProgressTarget> changed = changedTargets.stream()
                .map(t -> new Contracts.ProgressTarget(t.getTargetId(), t.getLang(), t.getStatus().name())).toList();
        var progress = new Contracts.JobProgressEvent("job.progress", job.getCorrelationId(),
                new Contracts.ProgressData(job.getJobId(), job.getStatus().name(), Instant.now(), changed));
        publisher.publishEvent(progress);
    }
}
