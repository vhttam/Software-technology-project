package vn.sgu.narration.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "narration_job_targets", uniqueConstraints =
        @UniqueConstraint(name = "UQ_narration_targets_job_lang", columnNames = {"job_id", "lang"}), indexes = {
        @Index(name = "IX_narration_targets_job_status", columnList = "job_id,status"),
        @Index(name = "IX_narration_targets_status_updated", columnList = "status,updated_at"),
        @Index(name = "IX_narration_targets_lang_status", columnList = "lang,status")
})
public class JobTargetEntity {
    @Id
    @Column(name = "target_id", length = 64, nullable = false)
    private String targetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private NarrationJobEntity job;

    @Column(name = "lang", length = 32, nullable = false)
    private String lang;

    @Column(name = "voice_id", length = 128, nullable = false)
    private String voiceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TargetStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "translation_id", length = 128)
    private String translationId;

    @Column(name = "audio_id", length = 128)
    private String audioId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobTargetEntity() {}

    public JobTargetEntity(String targetId, String lang, String voiceId, Instant now) {
        this.targetId = targetId;
        this.lang = lang;
        this.voiceId = voiceId;
        this.status = TargetStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void attachTo(NarrationJobEntity job) { this.job = job; }

    public void moveTo(TargetStatus next, String errorCode, Instant now) {
        boolean withdrawnPublishedResult = status == TargetStatus.PUBLISHED && next == TargetStatus.CANCELLED;
        if (status.isTerminal() && status != next && !withdrawnPublishedResult) return;
        if (!next.isTerminal() && next.rank() < status.rank()) return;
        this.status = next;
        this.errorCode = errorCode;
        this.updatedAt = now;
    }

    public void setTranslationIdIfAbsent(String translationId) {
        if (this.translationId == null && translationId != null) this.translationId = translationId;
    }

    public void setAudioIdIfAbsent(String audioId) {
        if (this.audioId == null && audioId != null) this.audioId = audioId;
    }

    public String getTargetId() { return targetId; }
    public NarrationJobEntity getJob() { return job; }
    public String getLang() { return lang; }
    public String getVoiceId() { return voiceId; }
    public TargetStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getTranslationId() { return translationId; }
    public String getAudioId() { return audioId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
