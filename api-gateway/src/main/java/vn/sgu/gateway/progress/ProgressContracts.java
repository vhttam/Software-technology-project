package vn.sgu.gateway.progress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProgressContracts {
    private ProgressContracts() {}

    public record ProgressTarget(@NotBlank String targetId, @NotBlank String lang, @NotBlank String status) {}
    public record ProgressData(@NotBlank String jobId, @NotBlank String status, @NotNull Instant updatedAt,
                               @Size(max = 100) List<@Valid ProgressTarget> targets) {}
    public record JobProgressEvent(@NotBlank String event, @NotBlank String correlationId,
                                   @NotNull @Valid ProgressData data) {}
}
