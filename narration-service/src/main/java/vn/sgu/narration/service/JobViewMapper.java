package vn.sgu.narration.service;

import org.springframework.stereotype.Component;
import vn.sgu.narration.api.Contracts;
import vn.sgu.narration.domain.JobTargetEntity;
import vn.sgu.narration.domain.NarrationJobEntity;

@Component
public class JobViewMapper {
    public Contracts.JobView toView(NarrationJobEntity job) {
        return new Contracts.JobView(job.getJobId(), job.getContentId(), job.getContentVersion(),
                job.getStatus().name(), job.getCreatedAt(), job.getUpdatedAt(),
                job.getTargets().stream().map(this::toView).toList());
    }

    public Contracts.TargetView toView(JobTargetEntity target) {
        return new Contracts.TargetView(target.getTargetId(), target.getLang(), target.getVoiceId(),
                target.getStatus().name(), target.getErrorCode(), target.getUpdatedAt());
    }
}
