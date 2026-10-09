package vn.sgu.narration.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "narration_jobs", indexes = {
        @Index(name = "IX_narration_jobs_content_version_status", columnList = "content_id,content_version,status"),
        @Index(name = "IX_narration_jobs_status_created", columnList = "status,created_at")
})
public class NarrationJobEntity {
    @Id
    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;

    @Column(name = "content_id", length = 128, nullable = false)
    private String contentId;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private JobStatus status;

    @Column(name = "created_by", length = 128, nullable = false)
    private String createdBy;

    @Column(name = "correlation_id", length = 128, nullable = false)
    private String correlationId;

    @Column(name = "retry_of_job_id", length = 64)
    private String retryOfJobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<JobTargetEntity> targets = new ArrayList<>();

    protected NarrationJobEntity() {}

    public NarrationJobEntity(String jobId, String contentId, int contentVersion,
                              String createdBy, String correlationId, String retryOfJobId, Instant now) {
        this.jobId = jobId;
        this.contentId = contentId;
        this.contentVersion = contentVersion;
        this.status = JobStatus.PENDING;
        this.createdBy = createdBy;
        this.correlationId = correlationId;
        this.retryOfJobId = retryOfJobId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addTarget(JobTargetEntity target) {
        targets.add(target);
        target.attachTo(this);
    }

    public void setStatus(JobStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
        this.finishedAt = status.isTerminal() ? now : null;
    }

    public void touch(Instant now) { this.updatedAt = now; }
    public String getJobId() { return jobId; }
    public String getContentId() { return contentId; }
    public int getContentVersion() { return contentVersion; }
    public JobStatus getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public String getCorrelationId() { return correlationId; }
    public String getRetryOfJobId() { return retryOfJobId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public List<JobTargetEntity> getTargets() { return targets; }
}
