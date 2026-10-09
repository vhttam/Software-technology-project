package vn.sgu.gateway.progress;

import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import vn.sgu.gateway.progress.ProgressContracts.JobProgressEvent;
import vn.sgu.gateway.progress.ProgressContracts.ProgressData;
import vn.sgu.gateway.progress.ProgressContracts.ProgressTarget;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressHubTest {
    @Test
    void publishesProgressToJobSpecificSseStream() throws Exception {
        ProgressHub hub = new ProgressHub();
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<ServerSentEvent<JobProgressEvent>> actual = new AtomicReference<>();
        var subscription = hub.stream("job-1").subscribe(event -> {
            actual.set(event);
            received.countDown();
        });
        Instant updatedAt = Instant.parse("2026-10-09T10:00:00Z");
        JobProgressEvent event = new JobProgressEvent("job.progress", "corr-1",
                new ProgressData("job-1", "PROCESSING", updatedAt,
                        List.of(new ProgressTarget("target-1", "en", "SYNTHESIZING"))));

        hub.publish(event);

        assertThat(received.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(actual.get().event()).isEqualTo("job.progress");
        assertThat(actual.get().id()).isEqualTo(updatedAt.toString());
        assertThat(actual.get().data()).isEqualTo(event);
        subscription.dispose();
    }

    @Test
    void publishingWithoutSubscribersIsSafeAndDoesNotLeakToOtherJobs() {
        ProgressHub hub = new ProgressHub();
        CountDownLatch received = new CountDownLatch(1);
        var subscription = hub.stream("job-1").subscribe(ignored -> received.countDown());

        hub.publish(new JobProgressEvent("job.progress", "corr-2",
                new ProgressData("job-2", "PROCESSING", Instant.now(), List.of())));

        assertThat(received.getCount()).isEqualTo(1);
        subscription.dispose();
    }
}
