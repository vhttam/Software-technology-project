package vn.sgu.narration.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_records", uniqueConstraints = @UniqueConstraint(
        name = "UQ_idempotency_scope", columnNames = {"user_id", "operation", "idempotency_key"}),
        indexes = @Index(name = "IX_idempotency_expiry", columnList = "expires_at"))
public class IdempotencyEntity {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;
    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;
    @Column(name = "operation", length = 64, nullable = false)
    private String operation;
    @Column(name = "idempotency_key", length = 255, nullable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;
    @Column(name = "job_id", length = 64, nullable = false)
    private String jobId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyEntity() {}

    public IdempotencyEntity(String userId, String operation, String key, String requestHash,
                             String jobId, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.operation = operation;
        this.idempotencyKey = key;
        this.requestHash = requestHash;
        this.jobId = jobId;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public String getRequestHash() { return requestHash; }
    public String getJobId() { return jobId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
