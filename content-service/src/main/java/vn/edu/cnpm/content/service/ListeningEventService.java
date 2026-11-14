package vn.edu.cnpm.content.service;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.content.api.ApiException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ListeningEventService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> ALLOWED_EVENT_TYPES = Set.of("STARTED", "COMPLETED");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    // In-memory rate limiting / anti-spam: visitorId -> lastEventTimestamp
    private final ConcurrentHashMap<String, Long> rateLimitMap = new ConcurrentHashMap<>();

    public ListeningEventService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Object> recordEvent(
            String visitorId,
            String poiId,
            String contentId,
            Integer version,
            String lang,
            String audioId,
            String eventType,
            Integer listenedSeconds,
            Integer durationSeconds
    ) {
        // Basic input validation
        if (visitorId == null || visitorId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISITOR_ID", "visitor_id không được để trống");
        }
        if (poiId == null || poiId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POI_ID", "poi_id không được để trống");
        }
        if (lang == null || lang.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LANG", "lang không được để trống");
        }
        String normalizedType = eventType != null ? eventType.trim().toUpperCase(Locale.ROOT) : "";
        if (!ALLOWED_EVENT_TYPES.contains(normalizedType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EVENT_TYPE", "event_type phải là STARTED hoặc COMPLETED");
        }

        // Basic anti-spam: reject events from same visitor within 500ms
        long nowMs = System.currentTimeMillis();
        Long lastTime = rateLimitMap.put(visitorId, nowMs);
        if (lastTime != null && (nowMs - lastTime) < 500) {
            // Anti-spam throttled: return ack silently or throw 429
            return Map.of("status", "THROTTLED", "message", "Event dropped due to rate limit");
        }
        // Cleanup old entries
        if (rateLimitMap.size() > 10_000) {
            rateLimitMap.entrySet().removeIf(e -> (nowMs - e.getValue()) > 60_000);
        }

        String eventId = UUID.randomUUID().toString();
        String occurredAt = clock.instant().toString(); // ISO UTC string

        int listened = listenedSeconds != null && listenedSeconds >= 0 ? listenedSeconds : 0;
        int duration = durationSeconds != null && durationSeconds >= 0 ? durationSeconds : 0;

        String safeVisitorId = visitorId.length() > 64 ? visitorId.substring(0, 64) : visitorId;
        String safePoiId = poiId.length() > 36 ? poiId.substring(0, 36) : poiId;
        String safeContentId = (contentId != null && contentId.length() > 36) ? contentId.substring(0, 36) : contentId;
        String safeLang = lang.length() > 10 ? lang.substring(0, 10).toLowerCase(Locale.ROOT) : lang.toLowerCase(Locale.ROOT);
        String safeAudioId = (audioId != null && audioId.length() > 36) ? audioId.substring(0, 36) : audioId;

        jdbc.update(
                "INSERT INTO listening_events (id, visitor_id, poi_id, content_id, version, lang, audio_id, event_type, listened_seconds, duration_seconds, occurred_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, safeVisitorId, safePoiId, safeContentId, version, safeLang, safeAudioId, normalizedType, listened, duration, occurredAt
        );

        return Map.of(
                "id", eventId,
                "status", "RECORDED",
                "occurredAt", occurredAt
        );
    }

    public Map<String, Object> getListeningAnalytics(String fromTime, String toTime) {
        Instant fromInstant;
        Instant toInstant;
        Instant now = clock.instant();

        try {
            fromInstant = (fromTime != null && !fromTime.isBlank())
                    ? Instant.parse(fromTime)
                    : now.minus(java.time.Duration.ofDays(30));
        } catch (DateTimeParseException e) {
            fromInstant = now.minus(java.time.Duration.ofDays(30));
        }

        try {
            toInstant = (toTime != null && !toTime.isBlank())
                    ? Instant.parse(toTime)
                    : now.plus(java.time.Duration.ofDays(1));
        } catch (DateTimeParseException e) {
            toInstant = now.plus(java.time.Duration.ofDays(1));
        }

        String fromStr = fromInstant.toString();
        String toStr = toInstant.toString();

        // 1. Language Share (%)
        List<Map<String, Object>> langCounts = jdbc.queryForList(
                "SELECT lang, COUNT(*) as cnt FROM listening_events WHERE occurred_at >= ? AND occurred_at <= ? GROUP BY lang",
                fromStr, toStr
        );

        long totalEvents = 0;
        for (Map<String, Object> row : langCounts) {
            Number c = (Number) row.get("cnt");
            if (c != null) totalEvents += c.longValue();
        }

        List<Map<String, Object>> languageShares = new ArrayList<>();
        if (totalEvents > 0) {
            for (Map<String, Object> row : langCounts) {
                String l = (String) row.get("lang");
                Number c = (Number) row.get("cnt");
                long count = c != null ? c.longValue() : 0;
                double pct = Math.round((count * 1000.0) / totalEvents) / 10.0;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("lang", l);
                item.put("count", count);
                item.put("percentage", pct);
                languageShares.add(item);
            }
            languageShares.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        }

        // 2. Completion rate = COMPLETED / STARTED
        Long startedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM listening_events WHERE event_type = 'STARTED' AND occurred_at >= ? AND occurred_at <= ?",
                Long.class, fromStr, toStr
        );
        Long completedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM listening_events WHERE event_type = 'COMPLETED' AND occurred_at >= ? AND occurred_at <= ?",
                Long.class, fromStr, toStr
        );

        long started = startedCount != null ? startedCount : 0;
        long completed = completedCount != null ? completedCount : 0;
        double completionRate = started > 0 ? Math.min(100.0, Math.round((completed * 1000.0) / started) / 10.0) : 0.0;

        // 3. Hourly listening counts (Asia/Ho_Chi_Minh timezone)
        // Retrieve all event timestamps within range to compute exact local hourly counts and 90-minute peak
        List<String> eventTimes = jdbc.queryForList(
                "SELECT occurred_at FROM listening_events WHERE occurred_at >= ? AND occurred_at <= ? ORDER BY occurred_at ASC",
                String.class, fromStr, toStr
        );

        int[] hourlyBuckets = new int[24];
        List<ZonedDateTime> eventDateTimes = new ArrayList<>(eventTimes.size());

        for (String t : eventTimes) {
            try {
                Instant inst = Instant.parse(t);
                ZonedDateTime zdt = inst.atZone(VIETNAM_ZONE);
                hourlyBuckets[zdt.getHour()]++;
                eventDateTimes.add(zdt);
            } catch (Exception ignored) {}
        }

        List<Map<String, Object>> hourlyDistribution = new ArrayList<>();
        int maxHourlyCount = 0;
        for (int h = 0; h < 24; h++) {
            if (hourlyBuckets[h] > maxHourlyCount) {
                maxHourlyCount = hourlyBuckets[h];
            }
        }

        for (int h = 0; h < 24; h++) {
            Map<String, Object> hItem = new LinkedHashMap<>();
            hItem.put("hour", h);
            hItem.put("label", String.format(Locale.ROOT, "%02dh", h));
            hItem.put("count", hourlyBuckets[h]);
            hItem.put("percentage", maxHourlyCount > 0 ? (int) Math.round((hourlyBuckets[h] * 100.0) / maxHourlyCount) : 0);
            hourlyDistribution.add(hItem);
        }

        // 4. 90-minute peak interval
        // Find 90-minute window with the highest play count
        String peakInterval = "Chưa xác định";
        int peakPlays = 0;

        if (!eventDateTimes.isEmpty()) {
            // Sliding window of 90 minutes
            int n = eventDateTimes.size();
            int right = 0;
            int maxInWindow = 0;
            ZonedDateTime bestStart = eventDateTimes.get(0);

            for (int left = 0; left < n; left++) {
                ZonedDateTime leftTime = eventDateTimes.get(left);
                ZonedDateTime windowEnd = leftTime.plusMinutes(90);

                while (right < n && !eventDateTimes.get(right).isAfter(windowEnd)) {
                    right++;
                }

                int inWindow = right - left;
                if (inWindow > maxInWindow) {
                    maxInWindow = inWindow;
                    bestStart = leftTime;
                }
            }

            if (maxInWindow > 0) {
                peakPlays = maxInWindow;
                ZonedDateTime bestEnd = bestStart.plusMinutes(90);
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
                peakInterval = bestStart.format(fmt) + " - " + bestEnd.format(fmt);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalEvents", totalEvents);
        result.put("startedCount", started);
        result.put("completedCount", completed);
        result.put("completionRate", completionRate);
        result.put("languageShares", languageShares);
        result.put("hourlyDistribution", hourlyDistribution);
        result.put("peakInterval", peakInterval);
        result.put("peakPlays", peakPlays);
        result.put("from", fromStr);
        result.put("to", toStr);

        return result;
    }
}
