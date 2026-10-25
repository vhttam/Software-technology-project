package vn.edu.cnpm.content.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PresenceService {
    private static final long WINDOW_SECONDS = 35;
    private final ConcurrentHashMap<String, Long> onlineUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> listeningUsers = new ConcurrentHashMap<>();
    private final AtomicInteger peakToday = new AtomicInteger(0);
    private final AtomicInteger totalVisitsToday = new AtomicInteger(0);

    public Map<String, Object> heartbeat(String clientId, Boolean isListening, String poiId, String lang) {
        return heartbeat(clientId, isListening, poiId, lang, false);
    }

    public Map<String, Object> heartbeat(String clientId, Boolean isListening, String poiId, String lang, Boolean leave) {
        long now = Instant.now().getEpochSecond();
        if (clientId == null || clientId.isBlank()) {
            clientId = "anon-" + System.currentTimeMillis();
        }

        if (Boolean.TRUE.equals(leave)) {
            onlineUsers.remove(clientId);
            listeningUsers.remove(clientId);
        } else {
            boolean isNew = !onlineUsers.containsKey(clientId);
            onlineUsers.put(clientId, now);

            if (isNew) {
                totalVisitsToday.incrementAndGet();
            }

            if (Boolean.TRUE.equals(isListening)) {
                listeningUsers.put(clientId, now);
            } else {
                listeningUsers.remove(clientId);
            }
        }

        cleanup(now);

        int currentOnline = onlineUsers.size();
        peakToday.updateAndGet(current -> Math.max(current, currentOnline));

        return Map.of(
                "ack", true,
                "nextHeartbeatInterval", 20
        );
    }

    public Map<String, Object> getOnlineCount() {
        long now = Instant.now().getEpochSecond();
        cleanup(now);
        int active = onlineUsers.size();
        return Map.of(
                "activeUsers", active,
                "updatedAt", Instant.now().toString()
        );
    }

    public Map<String, Object> getMetrics() {
        long now = Instant.now().getEpochSecond();
        cleanup(now);
        int currentOnline = onlineUsers.size();
        int activeListening = listeningUsers.size();
        int peak = Math.max(currentOnline, peakToday.get());
        int visits = Math.max(peak, totalVisitsToday.get());

        return Map.of(
                "currentActiveUsers", currentOnline,
                "activeListeningUsers", activeListening,
                "peakToday", peak,
                "totalVisitsToday", visits,
                "windowSeconds", WINDOW_SECONDS,
                "updatedAt", Instant.now().toString()
        );
    }

    private void cleanup(long now) {
        long threshold = now - WINDOW_SECONDS;
        onlineUsers.entrySet().removeIf(entry -> entry.getValue() < threshold);
        listeningUsers.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
