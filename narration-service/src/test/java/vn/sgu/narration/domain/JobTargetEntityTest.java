package vn.sgu.narration.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JobTargetEntityTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void targetStartsPendingAndMovesThroughTranslationAndSynthesisToPublished() {
        JobTargetEntity target = new JobTargetEntity("t-1", "en", "voice-1", now);

        assertThat(target.getStatus()).isEqualTo(TargetStatus.PENDING);
        target.moveTo(TargetStatus.TRANSLATING, null, now.plusSeconds(1));
        target.moveTo(TargetStatus.SYNTHESIZING, null, now.plusSeconds(2));
        target.moveTo(TargetStatus.PUBLISHED, null, now.plusSeconds(3));

        assertThat(target.getStatus()).isEqualTo(TargetStatus.PUBLISHED);
        assertThat(target.getUpdatedAt()).isEqualTo(now.plusSeconds(3));
        assertThat(target.getErrorCode()).isNull();
    }

    @Test
    void targetDoesNotMoveBackwardsOrLeaveFailedTerminalState() {
        JobTargetEntity target = new JobTargetEntity("t-1", "en", "voice-1", now);
        target.moveTo(TargetStatus.SYNTHESIZING, null, now.plusSeconds(1));
        target.moveTo(TargetStatus.TRANSLATING, null, now.plusSeconds(2));
        target.moveTo(TargetStatus.FAILED, "TIMEOUT", now.plusSeconds(3));
        target.moveTo(TargetStatus.PUBLISHED, null, now.plusSeconds(4));

        assertThat(target.getStatus()).isEqualTo(TargetStatus.FAILED);
        assertThat(target.getErrorCode()).isEqualTo("TIMEOUT");
        assertThat(target.getUpdatedAt()).isEqualTo(now.plusSeconds(3));
    }

    @Test
    void cancellationCanWithdrawPublishedResultButNotOverrideFailedResult() {
        JobTargetEntity published = new JobTargetEntity("t-1", "en", "voice-1", now);
        published.moveTo(TargetStatus.PUBLISHED, null, now.plusSeconds(1));
        published.moveTo(TargetStatus.CANCELLED, null, now.plusSeconds(2));

        JobTargetEntity failed = new JobTargetEntity("t-2", "fr", "voice-2", now);
        failed.moveTo(TargetStatus.FAILED, "PROVIDER_ERROR", now.plusSeconds(1));
        failed.moveTo(TargetStatus.CANCELLED, null, now.plusSeconds(2));

        assertThat(published.getStatus()).isEqualTo(TargetStatus.CANCELLED);
        assertThat(failed.getStatus()).isEqualTo(TargetStatus.FAILED);
    }
}
