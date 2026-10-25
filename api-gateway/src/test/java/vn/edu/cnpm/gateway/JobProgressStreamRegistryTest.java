package vn.edu.cnpm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;
import vn.edu.cnpm.gateway.sse.JobProgressStreamRegistry;

class JobProgressStreamRegistryTest {
    @Test
    void callbackIsDeliveredOnlyToSubscriberForMatchingJob() {
        JobProgressStreamRegistry registry = new JobProgressStreamRegistry();
        Map<String, Object> progress = Map.of("jobId", "job-a", "status", "PROCESSING");

        StepVerifier.create(registry.subscribe("job-a").take(1))
                .then(() -> registry.publish("job-b", Map.of("jobId", "job-b")))
                .then(() -> registry.publish("job-a", progress))
                .assertNext(event -> assertThat(event).isEqualTo(progress))
                .verifyComplete();
    }
}
