package vn.edu.cnpm.tts.health;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.cnpm.tts.service.AudioQualityValidator;

@RestController
public class HealthController {
    private final AudioQualityValidator audioValidator;

    public HealthController(AudioQualityValidator audioValidator) {
        this.audioValidator = audioValidator;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        if (!audioValidator.isHealthyForCurrentMode()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthResponse("error", new HealthData("DOWN", audioValidator.getUnhealthyReason())));
        }
        return ResponseEntity.ok(new HealthResponse("success", new HealthData("UP", null)));
    }

    public record HealthResponse(String status, HealthData data) {}
    public record HealthData(String state, String reason) {}
}
