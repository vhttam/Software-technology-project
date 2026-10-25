package vn.edu.cnpm.runner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalJobWorker {
    private static final String NARRATION_DB = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/narrationdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";
    private static final String TRANSLATION_DB = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/translationdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";
    private static final String TTS_DB = "jdbc:h2:file:D:/CNPM/Software-technology-project/.local-data/h2/ttsdb;AUTO_SERVER=TRUE;MODE=MSSQLServer;DATABASE_TO_LOWER=TRUE";

    private static final String SERVICE_TOKEN = System.getenv().getOrDefault("NARRATION_SERVICE_TOKEN", "local-dev-narration-caller-token-change-me");
    private static final String AUDIO_DIR_RAW = System.getenv().getOrDefault("OBJECT_STORAGE_PATH", ".local-data/audio");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static long lastWatchdogSweep = 0;

    public static void main(String[] args) {
        System.out.println("[WORKER] LocalJobWorker started. Polling pending jobs...");
        System.out.println("[WORKER] Token: " + (SERVICE_TOKEN.isEmpty() ? "(empty)" : SERVICE_TOKEN.substring(0, Math.min(10, SERVICE_TOKEN.length())) + "..."));
        System.out.println("[WORKER] Audio storage path: " + getResolvedAudioDir().toAbsolutePath());

        // Startup watchdog sweep for overdue jobs
        sweepStalledTargets();

        while (true) {
            try {
                // Periodic watchdog sweep every 30 seconds
                if (System.currentTimeMillis() - lastWatchdogSweep > 30000) {
                    sweepStalledTargets();
                    lastWatchdogSweep = System.currentTimeMillis();
                }

                processPendingTargets();
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                System.out.println("[WORKER] Interrupted, shutting down.");
                break;
            } catch (Exception e) {
                System.err.println("[WORKER] Worker loop error: " + e.getMessage());
                try { Thread.sleep(3000); } catch (Exception ignored) {}
            }
        }
    }

    private static Path getResolvedAudioDir() {
        Path p = Paths.get(AUDIO_DIR_RAW);
        if (!p.isAbsolute()) {
            p = Paths.get("D:/CNPM/Software-technology-project").resolve(p);
        }
        return p.normalize();
    }

    private static boolean isNarrationDbReady(Connection conn) {
        try {
            DatabaseMetaData md = conn.getMetaData();
            try (ResultSet rs = md.getTables(null, null, "job_targets", null)) {
                if (rs.next()) return true;
            }
            try (ResultSet rs = md.getTables(null, null, "JOB_TARGETS", null)) {
                if (rs.next()) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void sweepStalledTargets() {
        try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
            if (!isNarrationDbReady(conn)) return;

            String nowStr = Instant.now().toString();
            Instant fiveMinutesAgo = Instant.now().minus(Duration.ofMinutes(5));

            String querySql = "SELECT t.target_id, t.job_id, t.lang, t.status, t.updated_at, j.correlation_id " +
                              "FROM job_targets t JOIN jobs j ON j.job_id = t.job_id " +
                              "WHERE t.status IN ('TRANSLATING', 'SYNTHESIZING', 'PENDING') AND j.status IN ('PENDING', 'PROCESSING')";

            List<StalledTarget> stalled = new ArrayList<>();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(querySql)) {
                while (rs.next()) {
                    String updatedAtStr = rs.getString("updated_at");
                    try {
                        Instant updated = Instant.parse(updatedAtStr);
                        if (updated.isBefore(fiveMinutesAgo)) {
                            stalled.add(new StalledTarget(
                                rs.getString("target_id"),
                                rs.getString("job_id"),
                                rs.getString("lang"),
                                rs.getString("status"),
                                rs.getString("correlation_id")
                            ));
                        }
                    } catch (Exception ignored) {}
                }
            }

            for (StalledTarget st : stalled) {
                System.out.println("[WORKER WATCHDOG] Timing out stalled target: " + st.targetId + " (" + st.lang + ", status: " + st.status + ")");
                markTargetFailed(st.targetId, st.jobId, "TIMEOUT", "Quá thời gian xử lý (hơn 5 phút)", st.correlationId);
            }
        } catch (Exception ex) {
            System.err.println("[WORKER WATCHDOG] Error in sweepStalledTargets: " + ex.getMessage());
        }
    }

    private static void processPendingTargets() throws Exception {
        List<PendingTarget> targets = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
            if (!isNarrationDbReady(conn)) {
                return;
            }

            String sql = "SELECT t.target_id, t.job_id, t.lang, t.voice_id, j.content_id, j.version, j.correlation_id " +
                         "FROM job_targets t JOIN jobs j ON j.job_id = t.job_id " +
                         "WHERE t.status = 'PENDING' AND j.status IN ('PENDING', 'PROCESSING')";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    targets.add(new PendingTarget(
                        rs.getString("target_id"),
                        rs.getString("job_id"),
                        rs.getString("lang"),
                        rs.getString("voice_id"),
                        rs.getString("content_id"),
                        rs.getInt("version"),
                        rs.getString("correlation_id")
                    ));
                }
            }
        }

        for (PendingTarget pt : targets) {
            processSingleTarget(pt);
        }
    }

    private static void processSingleTarget(PendingTarget pt) {
        try {
            LangDef ld = getLangDef(pt.lang);
            if (ld != null && !ld.enabled) {
                System.err.println("[WORKER] Target language is disabled in configuration: " + pt.lang);
                markTargetFailed(pt.targetId, pt.jobId, "LANGUAGE_NOT_ENABLED", "Ngôn ngữ đã bị tạm dừng hoặc chưa được kích hoạt", pt.correlationId);
                return;
            }

            System.out.println("[WORKER] Processing target: " + pt.targetId + " (" + pt.lang + ") for job: " + pt.jobId);
            String now = Instant.now().toString();

            // 1. Mark target TRANSLATING and job PROCESSING in narrationdb
            try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE jobs SET status = 'PROCESSING', updated_at = ? WHERE job_id = ? AND status = 'PENDING'")) {
                    ps.setString(1, now);
                    ps.setString(2, pt.jobId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE job_targets SET status = 'TRANSLATING', updated_at = ? WHERE target_id = ? AND status = 'PENDING'")) {
                    ps.setString(1, now);
                    ps.setString(2, pt.targetId);
                    ps.executeUpdate();
                }
            }
            notifyGatewayProgress(pt.jobId, pt.correlationId);

            // 2. Fetch content text from content-service (port 8082)
            String contentUri = "http://127.0.0.1:8082/internal/contents/" + pt.contentId + "/versions/" + pt.version;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(contentUri))
                    .header("X-Service-Token", SERVICE_TOKEN)
                    .header("X-Correlation-ID", pt.correlationId == null ? "" : pt.correlationId)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                System.err.println("[WORKER] Failed to fetch content: HTTP " + resp.statusCode() + " " + resp.body());
                markTargetFailed(pt.targetId, pt.jobId, "CONTENT_NOT_FOUND", "Không tìm thấy phiên bản nội dung tương ứng", pt.correlationId);
                return;
            }
            String body = resp.body();
            String textContent = extractJsonField(body, "textContent");
            String sourceLang = extractJsonField(body, "sourceLang");
            if (textContent == null || textContent.isBlank()) {
                textContent = "Nội dung thuyết minh mẫu.";
            }

            // 3. Translation logic (skip if target == source)
            String translatedText;
            if (sourceLang != null && sourceLang.trim().equalsIgnoreCase(pt.lang != null ? pt.lang.trim() : "")) {
                System.out.println("[WORKER] Target language identical to source (" + pt.lang + "). Skipping translation.");
                translatedText = textContent;
            } else {
                translatedText = translateText(textContent, sourceLang, pt.lang);
                System.out.println("[WORKER] Auto-translated [" + sourceLang + " -> " + pt.lang + "]: " 
                        + (translatedText.length() > 80 ? translatedText.substring(0, 80) + "..." : translatedText));
            }

            // 4. Save to translationdb
            String translationId = UUID.randomUUID().toString();
            try (Connection tConn = DriverManager.getConnection(TRANSLATION_DB, "sa", "")) {
                String querySql = "SELECT translation_id, text_content FROM translations WHERE content_id = ? AND version = ? AND lang = ?";
                boolean exists = false;
                try (PreparedStatement ps = tConn.prepareStatement(querySql)) {
                    ps.setString(1, pt.contentId);
                    ps.setInt(2, pt.version);
                    ps.setString(3, pt.lang);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            translationId = rs.getString("translation_id");
                            String oldText = rs.getString("text_content");
                            if (oldText != null && !oldText.isBlank() && !oldText.startsWith("[") && !oldText.contains("???")) {
                                translatedText = oldText;
                                exists = true;
                            }
                        }
                    }
                }
                if (!exists) {
                    String mergeSql = "MERGE INTO translations (translation_id, content_id, version, lang, text_content, created_at) KEY(content_id, version, lang) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = tConn.prepareStatement(mergeSql)) {
                        ps.setString(1, translationId);
                        ps.setString(2, pt.contentId);
                        ps.setInt(3, pt.version);
                        ps.setString(4, pt.lang);
                        ps.setString(5, translatedText);
                        ps.setString(6, now);
                        ps.executeUpdate();
                    }
                }
            }

            // 5. Update target to SYNTHESIZING in narrationdb
            now = Instant.now().toString();
            try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE job_targets SET status = 'SYNTHESIZING', translation_id = ?, updated_at = ? WHERE target_id = ?")) {
                    ps.setString(1, translationId);
                    ps.setString(2, now);
                    ps.setString(3, pt.targetId);
                    ps.executeUpdate();
                }
            }
            notifyGatewayProgress(pt.jobId, pt.correlationId);

            // 6. Generate REAL audible spoken audio file on disk
            String audioUuid = UUID.randomUUID().toString();
            String objectKey = "audio/" + audioUuid + ".mp3";
            File targetFile = getResolvedAudioDir().resolve(objectKey).toFile();
            targetFile.getParentFile().mkdirs();

            boolean speechOk = generateSpeechAudio(translatedText, pt.lang, pt.voiceId, targetFile);
            long fileSizeBytes = targetFile.exists() ? targetFile.length() : 0;
            double durationSec = estimateDuration(fileSizeBytes);
            int durationSeconds = Math.max(2, (int) Math.round(durationSec));

            // Post-synthesis quality validation
            boolean qualityOk = speechOk && validateAudioQuality(pt.lang, translatedText.length(), durationSec, targetFile);
            if (!qualityOk) {
                System.err.println("[WORKER] Audio quality validation failed for target: " + pt.targetId + " (" + pt.lang + ")");
                if (targetFile.exists()) targetFile.delete();
                markTargetFailed(pt.targetId, pt.jobId, "AUDIO_GENERATION_FAILED", "Tệp âm thanh tạo ra không đạt chuẩn chất lượng hoặc quá ngắn so với văn bản", pt.correlationId);
                return;
            }

            fileSizeBytes = targetFile.length();
            System.out.println("[WORKER] Saved audible audio file: " + targetFile.getAbsolutePath() + " (" + fileSizeBytes + " bytes, ~" + durationSeconds + "s)");

            // 7. Save audio metadata to ttsdb safely (update if exists for translation_id + voice_id)
            String audioId = UUID.randomUUID().toString();
            try (Connection ttsConn = DriverManager.getConnection(TTS_DB, "sa", "")) {
                String findExistingSql = "SELECT audio_id FROM audios WHERE translation_id = ? AND voice_id = ?";
                boolean audioExists = false;
                try (PreparedStatement ps = ttsConn.prepareStatement(findExistingSql)) {
                    ps.setString(1, translationId);
                    ps.setString(2, pt.voiceId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            audioId = rs.getString("audio_id");
                            audioExists = true;
                        }
                    }
                }

                if (audioExists) {
                    // Atomic replacement: update existing record with new audio file
                    String updateSql = "UPDATE audios SET object_key = ?, duration_seconds = ?, size_bytes = ?, created_at = ? WHERE audio_id = ?";
                    try (PreparedStatement ps = ttsConn.prepareStatement(updateSql)) {
                        ps.setString(1, objectKey);
                        ps.setInt(2, durationSeconds);
                        ps.setLong(3, fileSizeBytes);
                        ps.setString(4, now);
                        ps.setString(5, audioId);
                        ps.executeUpdate();
                    }
                    System.out.println("[WORKER] Replaced existing audio record: " + audioId);
                } else {
                    String insertSql = "INSERT INTO audios (audio_id, translation_id, voice_id, object_key, duration_seconds, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement ps = ttsConn.prepareStatement(insertSql)) {
                        ps.setString(1, audioId);
                        ps.setString(2, translationId);
                        ps.setString(3, pt.voiceId);
                        ps.setString(4, objectKey);
                        ps.setInt(5, durationSeconds);
                        ps.setLong(6, fileSizeBytes);
                        ps.setString(7, now);
                        ps.executeUpdate();
                    }
                    System.out.println("[WORKER] Inserted new audio record: " + audioId);
                }
            }

            // 8. Update target to PUBLISHED and finalize job in narrationdb
            now = Instant.now().toString();
            try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE job_targets SET status = 'PUBLISHED', translation_id = ?, audio_id = ?, updated_at = ? WHERE target_id = ?")) {
                    ps.setString(1, translationId);
                    ps.setString(2, audioId);
                    ps.setString(3, now);
                    ps.setString(4, pt.targetId);
                    ps.executeUpdate();
                }

                checkAndUpdateJobFinalStatus(conn, pt.jobId, now);

                // Mark outbox published if present
                try (PreparedStatement ps = conn.prepareStatement("UPDATE outbox_events SET published = 1 WHERE payload LIKE ?")) {
                    ps.setString(1, "%" + pt.targetId + "%");
                    ps.executeUpdate();
                }
            }

            // 9. Notify Gateway of progress
            notifyGatewayProgress(pt.jobId, pt.correlationId);

        } catch (Exception ex) {
            System.err.println("[WORKER] Error processing target " + pt.targetId + ": " + ex.getMessage());
            ex.printStackTrace();
            markTargetFailed(pt.targetId, pt.jobId, "INTERNAL_ERROR", "Lỗi nội bộ khi xử lý: " + ex.getMessage(), pt.correlationId);
        }
    }

    private static void markTargetFailed(String targetId, String jobId, String errorCode, String errorMessage, String correlationId) {
        try {
            String now = Instant.now().toString();
            try (Connection conn = DriverManager.getConnection(NARRATION_DB, "sa", "")) {
                boolean updated = false;
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE job_targets SET status = 'FAILED', error_code = ?, error_message = ?, updated_at = ? WHERE target_id = ?")) {
                    ps.setString(1, errorCode);
                    ps.setString(2, errorMessage);
                    ps.setString(3, now);
                    ps.setString(4, targetId);
                    ps.executeUpdate();
                    updated = true;
                } catch (Exception ex) {
                    // Fallback if error_message column is not present
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE job_targets SET status = 'FAILED', error_code = ?, updated_at = ? WHERE target_id = ?")) {
                        ps.setString(1, errorCode);
                        ps.setString(2, now);
                        ps.setString(3, targetId);
                        ps.executeUpdate();
                        updated = true;
                    }
                }
                if (updated) {
                    checkAndUpdateJobFinalStatus(conn, jobId, now);
                }
            }
            notifyGatewayProgress(jobId, correlationId);
        } catch (Exception e) {
            System.err.println("[WORKER] Failed to mark target FAILED: " + e.getMessage());
        }
    }

    private static void checkAndUpdateJobFinalStatus(Connection conn, String jobId, String now) throws Exception {
        List<String> statuses = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM job_targets WHERE job_id = ?")) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    statuses.add(rs.getString("status"));
                }
            }
        }
        boolean allFinal = statuses.stream().allMatch(s -> "PUBLISHED".equals(s) || "FAILED".equals(s) || "CANCELLED".equals(s));
        if (allFinal) {
            String finalJobStatus;
            if (statuses.stream().allMatch("PUBLISHED"::equals)) {
                finalJobStatus = "COMPLETED";
            } else if (statuses.contains("PUBLISHED")) {
                finalJobStatus = "PARTIALLY_COMPLETED";
            } else if (statuses.stream().allMatch("FAILED"::equals)) {
                finalJobStatus = "FAILED";
            } else if (statuses.stream().allMatch("CANCELLED"::equals)) {
                finalJobStatus = "CANCELLED";
            } else {
                finalJobStatus = "FAILED";
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE jobs SET status = ?, updated_at = ? WHERE job_id = ?")) {
                ps.setString(1, finalJobStatus);
                ps.setString(2, now);
                ps.setString(3, jobId);
                ps.executeUpdate();
            }
            System.out.println("[WORKER] Job " + jobId + " transitioned to " + finalJobStatus + "!");
        }
    }

    public static double estimateDuration(long fileSizeBytes) {
        // Google TTS 32kbps mono MP3 is ~4000 bytes/sec
        return (double) fileSizeBytes / 4000.0;
    }

    public static boolean validateAudioQuality(String lang, int charCount, double durationSec, File file) {
        if (file == null || !file.exists() || file.length() < 1000) return false;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            int zeroCount = 0;
            for (byte b : bytes) {
                if (b == 0) zeroCount++;
            }
            double silenceRatio = (double) zeroCount / bytes.length;
            if (silenceRatio > 0.40) {
                System.err.println("[WORKER] Audio has high silence ratio: " + (silenceRatio * 100) + "%");
                return false;
            }

            // Expected reading speeds (chars/sec) dynamically loaded from config/languages.json
            LangDef def = getLangDef(lang);
            double maxSpeed = (def != null && def.maxReadingSpeed > 0) ? def.maxReadingSpeed : 20.0;

            double minExpectedDuration = (double) charCount / maxSpeed;
            double threshold = 0.40 * minExpectedDuration;

            if (durationSec < threshold) {
                System.err.printf("[WORKER] Audio duration %.1fs is below minimum threshold %.1fs (min expected %.1fs)%n",
                        durationSec, threshold, minExpectedDuration);
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.isBlank()) return "";
        if (sourceLang != null && sourceLang.trim().equalsIgnoreCase(targetLang != null ? targetLang.trim() : "")) {
            return text;
        }
        String s = (sourceLang == null || sourceLang.isBlank()) ? "vi" : sourceLang.toLowerCase().trim();
        String t = (targetLang == null || targetLang.isBlank()) ? "en" : targetLang.toLowerCase().trim();

        // 1. Primary: Google Translate Web API using configured translationLocale
        try {
            LangDef targetDef = getLangDef(t);
            String tl = (targetDef != null && targetDef.translationLocale != null && !targetDef.translationLocale.isBlank())
                    ? targetDef.translationLocale : t;
            LangDef srcDef = getLangDef(s);
            String sl = (srcDef != null && srcDef.translationLocale != null && !srcDef.translationLocale.isBlank())
                    ? srcDef.translationLocale : s;

            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl="
                    + URLEncoder.encode(sl, StandardCharsets.UTF_8)
                    + "&tl=" + URLEncoder.encode(tl, StandardCharsets.UTF_8)
                    + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String parsed = parseGoogleTranslateResponse(resp.body());
                if (parsed != null && !parsed.isBlank() && !parsed.contains("???")) {
                    return parsed;
                }
            }
        } catch (Exception ex) {
            System.err.println("[WORKER] Google Translate API error: " + ex.getMessage());
        }

        // 2. Secondary fallback: MyMemory Translation API
        try {
            String langPair = s + "|" + t;
            String url = "https://api.mymemory.translated.net/get?q=" 
                    + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&langpair=" + URLEncoder.encode(langPair, StandardCharsets.UTF_8)
                    + "&de=cnpm_student_test_2026@gmail.com";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                String translated = extractJsonField(resp.body(), "translatedText");
                if (translated != null && !translated.isBlank() && !translated.contains("MYMEMORY WARNING")) {
                    return unescapeUnicode(unescapeHtml(translated));
                }
            }
        } catch (Exception ex) {
            System.err.println("[WORKER] MyMemory translation API fallback failed: " + ex.getMessage());
        }

        return text;
    }

    public static String parseGoogleTranslateResponse(String json) {
        if (json == null || !json.startsWith("[[[")) return json;
        int endOfSegments = json.indexOf("]],");
        if (endOfSegments < 0) endOfSegments = json.length();
        String segmentsBlock = json.substring(3, endOfSegments);

        StringBuilder sb = new StringBuilder();
        int idx = 0;
        while (idx < segmentsBlock.length()) {
            int qStart = segmentsBlock.indexOf("\"", idx);
            if (qStart < 0) break;
            int qEnd = findMatchingQuote(segmentsBlock, qStart + 1);
            if (qEnd < 0) break;
            String piece = segmentsBlock.substring(qStart + 1, qEnd);
            sb.append(unescapeJson(piece));

            int nextSeg = segmentsBlock.indexOf("],[", qEnd);
            if (nextSeg < 0) break;
            idx = nextSeg + 3;
        }
        return sb.length() > 0 ? sb.toString() : json;
    }

    private static int findMatchingQuote(String s, int start) {
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\r", "\r").replace("\\\\", "\\");
    }

    private static String unescapeUnicode(String input) {
        if (input == null) return null;
        StringBuilder b = new StringBuilder();
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 5 < len && input.charAt(i + 1) == 'u') {
                try {
                    int code = Integer.parseInt(input.substring(i + 2, i + 6), 16);
                    b.append((char) code);
                    i += 5;
                    continue;
                } catch (Exception ignored) {}
            }
            b.append(c);
        }
        return b.toString();
    }

    private static String unescapeHtml(String text) {
        return text.replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">");
    }

    private static boolean generateSpeechAudio(String text, String lang, String voiceId, File targetFile) {
        try {
            targetFile.getParentFile().mkdirs();
            String l = (lang == null || lang.isBlank()) ? "vi" : lang.toLowerCase().trim();

            // 1. Primary: High-quality Online Google TTS with CJK sentence chunking
            boolean onlineOk = generateSpeechViaOnlineTts(text, l, targetFile);
            if (onlineOk && targetFile.exists() && targetFile.length() > 1000) {
                System.out.println("[WORKER] Speech synthesized via Online TTS (" + l + "): " + targetFile.length() + " bytes");
                return true;
            }

            // 2. Secondary fallback: Local Windows Speech Synthesis (only if matching language voice is installed)
            if ("vi".equals(l) || "en".equals(l)) {
                System.out.println("[WORKER] Attempting local Windows TTS fallback for [" + l + "]...");
                boolean localOk = generateSpeechViaWindowsTts(text, l, voiceId, targetFile);
                if (localOk && targetFile.exists() && targetFile.length() > 1000) {
                    System.out.println("[WORKER] Speech synthesized via Windows TTS: " + targetFile.length() + " bytes");
                    return true;
                }
            }

        } catch (Exception ex) {
            System.err.println("[WORKER] Speech audio generation error: " + ex.getMessage());
        }
        return false;
    }

    private static boolean generateSpeechViaOnlineTts(String text, String lang, File targetFile) {
        try {
            LangDef def = getLangDef(lang);
            String ttsLang = (def != null && def.ttsLocale != null && !def.ttsLocale.isBlank())
                    ? def.ttsLocale : lang;

            List<String> chunks = splitTextForTts(text, lang);
            ByteArrayOutputStream audioBuf = new ByteArrayOutputStream();

            for (String chunk : chunks) {
                if (chunk.isBlank()) continue;
                String url = "https://translate.google.com/translate_tts?ie=UTF-8&tl=" 
                        + URLEncoder.encode(ttsLang, StandardCharsets.UTF_8)
                        + "&client=tw-ob&q=" + URLEncoder.encode(chunk, StandardCharsets.UTF_8);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() == 200 && resp.body().length > 200) {
                    audioBuf.write(resp.body());
                } else {
                    System.err.println("[WORKER] Google TTS chunk returned status " + resp.statusCode());
                }
            }

            byte[] total = audioBuf.toByteArray();
            if (total.length > 500) {
                try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                    fos.write(total);
                }
                return true;
            }
        } catch (Exception ex) {
            System.err.println("[WORKER] Online TTS exception: " + ex.getMessage());
        }
        return false;
    }

    public static List<String> splitTextForTts(String text, int maxChars) {
        return splitTextForTts(text, null, maxChars);
    }

    public static List<String> splitTextForTts(String text, String lang) {
        LangDef def = getLangDef(lang);
        int maxChars = (def != null && def.maxTtsChunkChars > 0) ? def.maxTtsChunkChars : 80;
        return splitTextForTts(text, lang, maxChars);
    }

    public static List<String> splitTextForTts(String text, String lang, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        LangDef def = getLangDef(lang);
        String sentDelim = (def != null && def.sentenceDelimiters != null && !def.sentenceDelimiters.isBlank())
                ? def.sentenceDelimiters : "[.!?;\n。！？；]";
        String clauseDelim = (def != null && def.clauseDelimiters != null && !def.clauseDelimiters.isBlank())
                ? def.clauseDelimiters : "[,，、]";

        // Split by primary sentence delimiters
        String[] sentences = text.split("(?<=" + sentDelim + ")\\s*");
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.length() <= maxChars) {
                chunks.add(trimmed);
            } else {
                // Secondary split by clause delimiters
                String[] clauses = trimmed.split("(?<=" + clauseDelim + ")\\s*");
                StringBuilder cur = new StringBuilder();
                for (String clause : clauses) {
                    if (cur.length() + clause.length() <= maxChars) {
                        cur.append(clause);
                    } else {
                        if (cur.length() > 0) {
                            chunks.add(cur.toString().trim());
                            cur.setLength(0);
                        }
                        if (clause.length() <= maxChars) {
                            cur.append(clause);
                        } else {
                            // Hard chunking by character boundary
                            int start = 0;
                            while (start < clause.length()) {
                                int end = Math.min(start + maxChars, clause.length());
                                chunks.add(clause.substring(start, end).trim());
                                start = end;
                            }
                        }
                    }
                }
                if (cur.length() > 0) {
                    chunks.add(cur.toString().trim());
                }
            }
        }
        return chunks;
    }

    private static boolean generateSpeechViaWindowsTts(String text, String lang, String voiceId, File targetFile) {
        Path tempPs = null;
        Path tempWav = null;
        try {
            tempPs = Files.createTempFile("tts_", ".ps1");
            tempWav = Files.createTempFile("tts_wav_", ".wav");
            String safeText = text.replace("'", "''").replace("\r", " ").replace("\n", " ");
            String wavPath = tempWav.toAbsolutePath().toString().replace("'", "''");
            String finalPath = targetFile.getAbsolutePath().replace("'", "''");

            String psContent = "$OutputEncoding = [System.Text.Encoding]::UTF8\n" +
                               "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
                               "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
                               "[Windows.Media.SpeechSynthesis.SpeechSynthesizer,Windows.Media,ContentType=WindowsRuntime] | Out-Null\n" +
                               "[Windows.Storage.Streams.DataReader,Windows.Storage.Streams,ContentType=WindowsRuntime] | Out-Null\n" +
                               "[Windows.Storage.StorageFile,Windows.Storage,ContentType=WindowsRuntime] | Out-Null\n" +
                               "[Windows.Media.MediaProperties.MediaEncodingProfile,Windows.Media,ContentType=WindowsRuntime] | Out-Null\n" +
                               "[Windows.Media.Transcoding.MediaTranscoder,Windows.Media,ContentType=WindowsRuntime] | Out-Null\n" +
                               "\n" +
                               "$asTaskOp = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n" +
                               "$asTaskActionWithProgress = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncActionWithProgress`1' })[0]\n" +
                               "Function Await-Op($op, $type) {\n" +
                               "    $m = $asTaskOp.MakeGenericMethod($type)\n" +
                               "    $t = $m.Invoke($null, @($op))\n" +
                               "    $t.Wait(15000) | Out-Null\n" +
                               "    $t.Result\n" +
                               "}\n" +
                               "Function Await-ActionWithProgress($action, $progressType) {\n" +
                               "    $m = $asTaskActionWithProgress.MakeGenericMethod($progressType)\n" +
                               "    $t = $m.Invoke($null, @($action))\n" +
                               "    $t.Wait(15000) | Out-Null\n" +
                               "}\n" +
                               "\n" +
                               "try {\n" +
                               "    $syn = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer\n" +
                               "    $voice = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices | Where-Object { $_.Language -like ('" + lang + "' + '*') } | Select-Object -First 1\n" +
                               "    if (-not $voice) { exit 1 }\n" +
                               "    $syn.Voice = $voice\n" +
                               "\n" +
                               "    $stream = Await-Op ($syn.SynthesizeTextToStreamAsync('" + safeText + "')) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])\n" +
                               "    $reader = New-Object Windows.Storage.Streams.DataReader($stream)\n" +
                               "    Await-Op ($reader.LoadAsync($stream.Size)) ([System.UInt32]) | Out-Null\n" +
                               "    $bytes = New-Object byte[] ($stream.Size)\n" +
                               "    $reader.ReadBytes($bytes)\n" +
                               "    $reader.Dispose()\n" +
                               "    $stream.Dispose()\n" +
                               "    $syn.Dispose()\n" +
                               "    [System.IO.File]::WriteAllBytes('" + wavPath + "', $bytes)\n" +
                               "} catch {\n" +
                               "    exit 1\n" +
                               "}\n" +
                               "\n" +
                               "try {\n" +
                               "    $srcFile = Await-Op ([Windows.Storage.StorageFile]::GetFileFromPathAsync('" + wavPath + "')) ([Windows.Storage.StorageFile])\n" +
                               "    $folder = Await-Op ([Windows.Storage.StorageFolder]::GetFolderFromPathAsync((Split-Path '" + finalPath + "'))) ([Windows.Storage.StorageFolder])\n" +
                               "    $destFile = Await-Op ($folder.CreateFileAsync((Split-Path '" + finalPath + "' -Leaf), [Windows.Storage.CreationCollisionOption]::ReplaceExisting)) ([Windows.Storage.StorageFile])\n" +
                               "\n" +
                               "    $profile = [Windows.Media.MediaProperties.MediaEncodingProfile]::CreateMp3([Windows.Media.MediaProperties.AudioEncodingQuality]::Medium)\n" +
                               "    $transcoder = New-Object Windows.Media.Transcoding.MediaTranscoder\n" +
                               "    $prepareOp = Await-Op ($transcoder.PrepareFileTranscodeAsync($srcFile, $destFile, $profile)) ([Windows.Media.Transcoding.PrepareTranscodeResult])\n" +
                               "    if ($prepareOp.CanTranscode) {\n" +
                               "        Await-ActionWithProgress ($prepareOp.TranscodeAsync()) ([System.Double])\n" +
                               "    }\n" +
                               "} catch {\n" +
                               "    exit 1\n" +
                               "}\n";

            byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] scriptBytes = psContent.getBytes(StandardCharsets.UTF_8);
            byte[] fullScript = new byte[bom.length + scriptBytes.length];
            System.arraycopy(bom, 0, fullScript, 0, bom.length);
            System.arraycopy(scriptBytes, 0, fullScript, bom.length, scriptBytes.length);
            Files.write(tempPs, fullScript);

            ProcessBuilder pb = new ProcessBuilder("powershell", "-ExecutionPolicy", "Bypass", "-File", tempPs.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(25, java.util.concurrent.TimeUnit.SECONDS);

            if (finished && p.exitValue() == 0 && targetFile.exists() && targetFile.length() > 1000) {
                return true;
            }
        } catch (Exception ex) {
            System.err.println("[WORKER] Windows TTS exception: " + ex.getMessage());
        } finally {
            try {
                if (tempPs != null) Files.deleteIfExists(tempPs);
                if (tempWav != null) Files.deleteIfExists(tempWav);
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void notifyGatewayProgress(String jobId, String correlationId) {
        try {
            String jobUri = "http://127.0.0.1:8083/api/v1/jobs/" + jobId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jobUri))
                    .header("X-Service-Token", SERVICE_TOKEN)
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                String dataJson = extractJsonDataBlock(body);
                if (dataJson != null) {
                    String payload = "{\"event\":\"job.progress\",\"correlationId\":\"" + (correlationId == null ? "" : correlationId) + "\",\"data\":" + dataJson + "}";
                    HttpRequest cbReq = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:8080/internal/callbacks/job-progress"))
                            .header("Content-Type", "application/json")
                            .header("X-Service-Token", SERVICE_TOKEN)
                            .header("X-Correlation-ID", correlationId == null ? "" : correlationId)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                    HTTP.send(cbReq, HttpResponse.BodyHandlers.discarding());
                }
            }
        } catch (Exception ignored) {}
    }

    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return matcher.group(1);
        return null;
    }

    private static String extractJsonDataBlock(String json) {
        int idx = json.indexOf("\"data\":");
        if (idx < 0) return null;
        int start = json.indexOf('{', idx);
        if (start < 0) return null;
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    public static class LangDef {
        public String code;
        public String nameVi;
        public String nativeName;
        public String translationLocale;
        public String ttsLocale;
        public String direction = "ltr";
        public String fontStack = "'Times New Roman', Times, serif";
        public String sentenceDelimiters = "[.!?;\n。！？；]";
        public String clauseDelimiters = "[,，、]";
        public double minReadingSpeed = 10.0;
        public double maxReadingSpeed = 20.0;
        public int maxTtsChunkChars = 80;
        public int maxTtsChunkBytes = 120;
        public boolean enabled = true;
    }

    private static final java.util.Map<String, LangDef> LANG_CONFIGS = loadLangConfigs();

    private static Path resolveLanguagesConfigFile() {
        String env = System.getenv("LANGUAGE_CONFIG_PATH");
        if (env != null && !env.isBlank()) {
            Path ep = Paths.get(env);
            if (Files.exists(ep)) return ep;
        }
        Path[] candidates = new Path[]{
            Paths.get("config/languages.json"),
            Paths.get("../config/languages.json"),
            Paths.get("D:/CNPM/Software-technology-project/config/languages.json")
        };
        for (Path p : candidates) {
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static java.util.Map<String, LangDef> loadLangConfigs() {
        java.util.Map<String, LangDef> map = new java.util.concurrent.ConcurrentHashMap<>();
        Path p = resolveLanguagesConfigFile();
        if (p != null) {
            try {
                String json = Files.readString(p, StandardCharsets.UTF_8);
                int idx = 0;
                while (idx < json.length()) {
                    int start = json.indexOf('{', idx);
                    if (start < 0) break;
                    int end = findMatchingBrace(json, start);
                    if (end < 0) break;
                    String block = json.substring(start, end + 1);
                    LangDef def = parseLangBlock(block);
                    if (def != null && def.code != null && !def.code.isBlank()) {
                        map.put(def.code.toLowerCase().trim(), def);
                    }
                    idx = end + 1;
                }
                System.out.println("[WORKER] Loaded " + map.size() + " language configs from " + p.toAbsolutePath());
            } catch (Exception ex) {
                System.err.println("[WORKER] Failed to load languages config: " + ex.getMessage());
            }
        }
        return map;
    }

    private static int findMatchingBrace(String s, int start) {
        int depth = 0;
        boolean inQuote = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuote) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inQuote = false;
                }
            } else {
                if (c == '"') {
                    inQuote = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private static LangDef parseLangBlock(String block) {
        LangDef d = new LangDef();
        d.code = extractJsonField(block, "code");
        if (d.code == null || d.code.isBlank()) return null;
        d.nameVi = extractJsonField(block, "nameVi");
        d.nativeName = extractJsonField(block, "nativeName");
        d.translationLocale = extractJsonField(block, "translationLocale");
        d.ttsLocale = extractJsonField(block, "ttsLocale");
        d.direction = extractJsonField(block, "direction");
        d.fontStack = extractJsonField(block, "fontStack");
        d.sentenceDelimiters = extractJsonField(block, "sentenceDelimiters");
        d.clauseDelimiters = extractJsonField(block, "clauseDelimiters");

        Double minSpeed = extractJsonNumber(block, "minReadingSpeed");
        if (minSpeed != null) d.minReadingSpeed = minSpeed;
        Double maxSpeed = extractJsonNumber(block, "maxReadingSpeed");
        if (maxSpeed != null) d.maxReadingSpeed = maxSpeed;

        Integer maxChars = extractJsonInt(block, "maxTtsChunkChars");
        if (maxChars != null) d.maxTtsChunkChars = maxChars;
        Integer maxBytes = extractJsonInt(block, "maxTtsChunkBytes");
        if (maxBytes != null) d.maxTtsChunkBytes = maxBytes;

        Boolean enabled = extractJsonBool(block, "enabled");
        if (enabled != null) d.enabled = enabled;
        return d;
    }

    private static Double extractJsonNumber(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try { return Double.parseDouble(matcher.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Integer extractJsonInt(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*([0-9]+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Boolean extractJsonBool(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Boolean.parseBoolean(matcher.group(1));
        }
        return null;
    }

    private static LangDef getLangDef(String lang) {
        if (lang == null || lang.isBlank()) return null;
        return LANG_CONFIGS.get(lang.toLowerCase().trim());
    }

    private record PendingTarget(
        String targetId,
        String jobId,
        String lang,
        String voiceId,
        String contentId,
        int version,
        String correlationId
    ) {}

    private record StalledTarget(
        String targetId,
        String jobId,
        String lang,
        String status,
        String correlationId
    ) {}
}
