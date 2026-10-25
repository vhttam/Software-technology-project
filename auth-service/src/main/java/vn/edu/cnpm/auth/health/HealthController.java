package vn.edu.cnpm.auth.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("success", new HealthData("UP"));
    }

    public record HealthResponse(String status, HealthData data) {}
    public record HealthData(String state) {}
}
