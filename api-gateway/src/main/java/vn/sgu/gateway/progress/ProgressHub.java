package vn.sgu.gateway.progress;

import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import vn.sgu.gateway.progress.ProgressContracts.JobProgressEvent;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ProgressHub {
    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<JobProgressEvent>> stream(String jobId) {
        Channel channel = channels.computeIfAbsent(jobId, ignored -> new Channel());
        channel.lastUsed = Instant.now();
        return channel.sink.asFlux()
                .map(event -> ServerSentEvent.<JobProgressEvent>builder(event)
                        .event(event.event()).id(event.data().updatedAt().toString()).build())
                .doOnSubscribe(ignored -> channel.lastUsed = Instant.now())
                .doFinally(ignored -> channel.lastUsed = Instant.now());
    }

    public void publish(JobProgressEvent event) {
        Channel channel = channels.get(event.data().jobId());
        if (channel == null) return;
        channel.lastUsed = Instant.now();
        channel.sink.tryEmitNext(event);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT10M")
    public void evictInactiveChannels() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        channels.entrySet().removeIf(entry -> entry.getValue().lastUsed.isBefore(cutoff)
                && entry.getValue().sink.currentSubscriberCount() == 0);
    }

    private static final class Channel {
        private final Sinks.Many<JobProgressEvent> sink = Sinks.many().multicast().directBestEffort();
        private volatile Instant lastUsed = Instant.now();
    }
}
