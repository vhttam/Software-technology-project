package vn.sgu.narration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "cancelled_jobs")
public class CancelledJobEntity {
    @Id
    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;
    @Column(name = "cancelled_at", nullable = false)
    private Instant cancelledAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected CancelledJobEntity() {}
    public CancelledJobEntity(String jobId, Instant cancelledAt, Instant expiresAt) {
        this.jobId = jobId;
        this.cancelledAt = cancelledAt;
        this.expiresAt = expiresAt;
    }
}
