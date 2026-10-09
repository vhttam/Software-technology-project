package vn.sgu.narration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.sgu.narration.service.JobApplicationService;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
    private final JobApplicationService jobs;

    public JobController(JobApplicationService jobs) { this.jobs = jobs; }

    @PostMapping
    public ResponseEntity<ApiEnvelope<Contracts.JobView>> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody Contracts.CreateJobRequest request) {
        Contracts.JobView view = jobs.create(userId, correlationId == null ? "unknown" : correlationId,
                idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.success(view));
    }

    @GetMapping("/{jobId}")
    public ApiEnvelope<Contracts.JobView> get(@PathVariable String jobId) {
        return ApiEnvelope.success(jobs.get(jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public ApiEnvelope<Contracts.JobView> cancel(@PathVariable String jobId) {
        return ApiEnvelope.success(jobs.cancel(jobId));
    }
}
