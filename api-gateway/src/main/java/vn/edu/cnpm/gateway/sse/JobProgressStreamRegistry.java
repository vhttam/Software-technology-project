package vn.edu.cnpm.gateway.sse;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class JobProgressStreamRegistry {
    private final ConcurrentHashMap<String, Set<Sinks.Many<Object>>> streams = new ConcurrentHashMap<>();

    public Flux<Object> subscribe(String jobId) {
        Sinks.Many<Object> sink = Sinks.many().unicast().onBackpressureBuffer();
        streams.computeIfAbsent(jobId, ignored -> ConcurrentHashMap.newKeySet()).add(sink);
        return sink.asFlux().doFinally(signal -> remove(jobId, sink));
    }

    public void publish(String jobId, Object eventData) {
        Set<Sinks.Many<Object>> subscribers = streams.get(jobId);
        if (subscribers == null) return;
        subscribers.forEach(sink -> sink.tryEmitNext(eventData));
    }

    private void remove(String jobId, Sinks.Many<Object> sink) {
        Set<Sinks.Many<Object>> subscribers = streams.get(jobId);
        if (subscribers == null) return;
        subscribers.remove(sink);
        if (subscribers.isEmpty()) streams.remove(jobId, subscribers);
    }
}
