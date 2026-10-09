package vn.sgu.gateway.progress;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import vn.sgu.gateway.progress.ProgressContracts.JobProgressEvent;
import vn.sgu.gateway.progress.ProgressContracts.ProgressData;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressControllerTest {
    @Test
    void acceptsCallbackOnlyWithValidInternalTokenAndPublishesEvent() throws Exception {
        ProgressHub hub = new ProgressHub();
        ProgressController controller = new ProgressController(hub, "narration-service-secret");
        CountDownLatch received = new CountDownLatch(1);
        var subscription = hub.stream("job-1").subscribe(ignored -> received.countDown());
        JobProgressEvent event = new JobProgressEvent("job.progress", "corr-1",
                new ProgressData("job-1", "PROCESSING", Instant.now(), List.of()));

        var accepted = controller.callback("narration-service-secret", event).block();

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
    }

    @Test
    void rejectsMissingOrIncorrectInternalToken() {
        ProgressController controller = new ProgressController(new ProgressHub(), "narration-service-secret");
        JobProgressEvent event = new JobProgressEvent("job.progress", "corr-1",
                new ProgressData("job-1", "PROCESSING", Instant.now(), List.of()));

        assertThat(controller.callback(null, event).block().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(controller.callback("wrong", event).block().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
