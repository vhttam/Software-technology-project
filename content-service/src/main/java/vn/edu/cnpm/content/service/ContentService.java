package vn.edu.cnpm.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.content.api.ApiException;
import vn.edu.cnpm.content.config.LanguageRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ContentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${app.services.narration-url:${NARRATION_SERVICE_URL:http://localhost:8083}}")
    private String narrationServiceUrl;

    @Value("${app.security.service-token:${CONTENT_SERVICE_TOKEN:content-token}}")
    private String contentServiceToken;

    public ContentService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public Map<String, Object> sourceLanguages() {
        return Map.of("languages", LanguageRegistry.getLanguageCodes());
    }

    public Map<String, Object> createPoi(String name, String description) {
        return createPoi(name, description, null, null, 30);
    }

    @Transactional
    public Map<String, Object> createPoi(String name, String description, Double latitude, Double longitude, Integer triggerRadius) {
        validatePoi(name, description, latitude, longitude);
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        String qr = null;
        int radius = (triggerRadius != null && triggerRadius >= 10 && triggerRadius <= 500) ? triggerRadius : 30;

        for (int attempt = 0; attempt < 3; attempt++) {
            String candidate = newQrCode();
            if (!jdbc.queryForList("SELECT poi_id FROM points_of_interest WHERE qr_code=?", candidate).isEmpty()) continue;
            try {
                jdbc.update("INSERT INTO points_of_interest(poi_id,name,description,qr_code,latitude,longitude,trigger_radius,created_at,updated_at,is_deleted) VALUES(?,?,?,?,?,?,?,?,?,0)",
                        id, name, description, candidate, latitude, longitude, radius, now.toString(), now.toString());
                qr = candidate;
                break;
            } catch (DataIntegrityViolationException ex) {
                // Retry if duplicate QR code generated simultaneously
            }
        }
        if (qr == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "QR_CODE_GENERATION_FAILED", "Không thể tạo mã QR, vui lòng thử lại");
        return poi(id, name, description, qr, latitude, longitude, radius, now.toString(), now.toString());
    }

    public Map<String, Object> getPoi(String id) {
        return findPoi(id).orElseThrow(this::poiMissing);
    }

    public Map<String, Object> updatePoi(String id, String name, String description) {
        return updatePoi(id, name, description, null, null, null);
    }

    @Transactional
    public Map<String, Object> updatePoi(String id, String name, String description, Double latitude, Double longitude, Integer triggerRadius) {
        validatePoi(name, description, latitude, longitude);
        lockPoi(id);
        Map<String, Object> current = findPoi(id).orElseThrow(this::poiMissing);
        String now = clock.instant().toString();

        Double newLat = latitude != null ? latitude : (Double) current.get("latitude");
        Double newLng = longitude != null ? longitude : (Double) current.get("longitude");
        Integer newRadius = triggerRadius != null ? triggerRadius : (Integer) current.get("triggerRadius");
        if (newRadius == null) newRadius = 30;

        jdbc.update("UPDATE points_of_interest SET name=?,description=?,latitude=?,longitude=?,trigger_radius=?,updated_at=? WHERE poi_id=?",
                name, description, newLat, newLng, newRadius, now, id);
        return poi(id, name, description, (String) current.get("qrCode"), newLat, newLng, newRadius, (String) current.get("createdAt"), now);
    }

    @Transactional
    public void deletePoi(String id) {
        lockPoi(id);
        if (findPoi(id).isEmpty()) throw poiMissing();
        Integer contentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contents WHERE poi_id=? AND is_deleted=0", Integer.class, id);
        if (contentCount != null && contentCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "POI_HAS_CONTENT",
                    "Điểm còn " + contentCount + " nội dung đang liên kết, hãy xóa nội dung trước");
        }
        String now = clock.instant().toString();
        jdbc.update("UPDATE points_of_interest SET is_deleted=1, deleted_at=?, updated_at=? WHERE poi_id=?", now, now, id);
    }

    public Map<String, Object> listPois(int page, int pageSize, String query) {
        validatePage(page, pageSize);
        List<Map<String, Object>> all = jdbc.query("SELECT * FROM points_of_interest WHERE is_deleted=0 ORDER BY name", this::poiRow);
        if (query != null && !query.isBlank()) {
            String q = LanguageRegistry.unaccent(query);
            all = all.stream().filter(p -> LanguageRegistry.unaccent((String) p.get("name")).contains(q)).toList();
        }
        return page(all, page, pageSize);
    }

    public Map<String, Object> getPoiCard(String poiId) {
        Map<String, Object> poi = findPoi(poiId).orElseThrow(this::poiMissing);
        Integer activeContent = jdbc.queryForObject("SELECT COUNT(*) FROM contents WHERE poi_id=? AND is_deleted=0", Integer.class, poiId);
        boolean hasNarration = activeContent != null && activeContent > 0;

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("poiId", poi.get("poiId"));
        card.put("poiName", poi.get("name"));
        card.put("description", poi.get("description"));
        card.put("latitude", poi.get("latitude"));
        card.put("longitude", poi.get("longitude"));
        card.put("qrCode", poi.get("qrCode"));
        card.put("qrImageUrl", "/api/v1/client/pois/" + poiId + "/qr-image?size=600");
        card.put("hasNarration", hasNarration);
        return card;
    }

    public Map<String, Object> listNearbyPois(Double lat, Double lng, double radius) {
        double userLat = lat != null ? lat : 10.7720;
        double userLng = lng != null ? lng : 106.7050;
        double scanRadius = radius > 0 ? radius : 1500.0;

        List<Map<String, Object>> activePois = jdbc.query("""
                SELECT p.* FROM points_of_interest p
                 WHERE p.is_deleted=0
                   AND EXISTS (SELECT 1 FROM contents c WHERE c.poi_id=p.poi_id AND c.is_deleted=0)
                ORDER BY p.name
                """, this::poiRow);

        List<Map<String, Object>> nearbyList = new ArrayList<>();
        for (Map<String, Object> poi : activePois) {
            Double pLat = (Double) poi.get("latitude");
            Double pLng = (Double) poi.get("longitude");
            if (pLat == null || pLng == null) continue;

            double dist = calculateHaversine(userLat, userLng, pLat, pLng);
            if (dist <= scanRadius) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("poiId", poi.get("poiId"));
                item.put("poiName", poi.get("name"));
                item.put("description", poi.get("description"));
                item.put("latitude", pLat);
                item.put("longitude", pLng);
                item.put("distanceMeters", Math.round(dist * 10.0) / 10.0);
                int triggerR = poi.get("triggerRadius") != null ? (Integer) poi.get("triggerRadius") : 30;
                item.put("isWithinTriggerRadius", dist <= triggerR);
                item.put("hasPublishedNarration", true);
                nearbyList.add(item);
            }
        }

        nearbyList.sort(Comparator.comparingDouble(a -> ((Number) a.get("distanceMeters")).doubleValue()));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("userLocation", Map.of("latitude", userLat, "longitude", userLng));
        res.put("radiusMeters", scanRadius);
        res.put("totalNearby", nearbyList.size());
        res.put("items", nearbyList);
        return res;
    }

    public Map<String, Object> quickCreateWithJob(
            String name, String description, Double latitude, Double longitude, Integer triggerRadius,
            String title, String sourceLang, String textContent,
            List<Map<String, String>> targets,
            String idempotencyKey, String userId, String correlationId) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Thiếu header Idempotency-Key");
        }
        validatePoi(name, description, latitude, longitude);
        validateContent(title, sourceLang, textContent);
        if (targets == null || targets.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGETS", "Danh sách targets không được rỗng");
        }

        String actualUserId = (userId != null && !userId.isBlank()) ? userId : "system";

        // Step 1: Local DB transaction for POI and Content v1
        Map<String, Object> poiResult = createPoi(name, description, latitude, longitude, triggerRadius);
        String poiId = (String) poiResult.get("poiId");

        Map<String, Object> contentResult;
        try {
            contentResult = createContent(poiId, title, sourceLang, textContent, actualUserId, correlationId);
        } catch (Exception ex) {
            deletePoiPhysically(poiId);
            throw ex;
        }

        String contentId = (String) contentResult.get("contentId");

        // Step 2: Trigger Job on Narration Service via REST
        Map<String, Object> jobResult;
        try {
            Map<String, Object> jobPayload = Map.of(
                    "contentId", contentId,
                    "expectedVersion", 1,
                    "targets", targets
            );
            String requestBody = json.writeValueAsString(jobPayload);

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(narrationServiceUrl + "/internal/jobs"))
                    .header("Content-Type", "application/json")
                    .header("X-Service-Token", contentServiceToken)
                    .header("Idempotency-Key", idempotencyKey)
                    .header("X-User-Id", actualUserId)
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));

            if (correlationId != null) {
                reqBuilder.header("X-Correlation-ID", correlationId);
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Narration service responded with " + response.statusCode() + ": " + response.body());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = json.readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            jobResult = data;
        } catch (Exception ex) {
            // Compensating transaction: roll back Content and POI
            rollbackQuickCreate(poiId, contentId);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "NARRATION_SERVICE_UNAVAILABLE",
                    "Không thể kết nối hoặc khởi tạo tác vụ thuyết minh trên Narration Service; đã hoàn nguyên dữ liệu.",
                    Map.of("error", ex.getMessage()));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> poiData = new LinkedHashMap<>(poiResult);
        poiData.put("qrImageUrl", "/api/v1/client/pois/" + poiId + "/qr-image?size=600");
        out.put("poi", poiData);
        out.put("content", Map.of(
                "contentId", contentId,
                "version", 1,
                "title", title,
                "sourceLang", sourceLang
        ));
        out.put("job", jobResult);
        return out;
    }

    private void rollbackQuickCreate(String poiId, String contentId) {
        try {
            jdbc.update("DELETE FROM content_versions WHERE content_id=?", contentId);
            jdbc.update("DELETE FROM contents WHERE content_id=?", contentId);
            jdbc.update("DELETE FROM points_of_interest WHERE poi_id=?", poiId);
        } catch (Exception e) {
            // Log rollback failure
            System.err.println("Compensating transaction failed: " + e.getMessage());
        }
    }

    private void deletePoiPhysically(String poiId) {
        try {
            jdbc.update("DELETE FROM points_of_interest WHERE poi_id=?", poiId);
        } catch (Exception ignored) {}
    }

    @Transactional
    public Map<String, Object> createContent(String poiId, String title, String sourceLang, String textContent, String createdBy, String correlationId) {
        validateContent(title, sourceLang, textContent);
        lockPoi(poiId);
        if (findPoi(poiId).isEmpty()) throw poiMissing();
        if (!jdbc.queryForList("SELECT content_id FROM contents WHERE poi_id=? AND is_deleted=0", poiId).isEmpty())
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_CONTENT_EXISTS", "Điểm tham quan đã có nội dung đang hoạt động");
        String id = UUID.randomUUID().toString(), now = clock.instant().toString();
        try {
            jdbc.update("INSERT INTO contents(content_id,poi_id,current_version,is_deleted,created_by,created_at,updated_at) VALUES(?,?,1,0,?,?,?)",
                    id, poiId, createdBy, now, now);
            jdbc.update("INSERT INTO content_versions(content_id,version,title,source_lang,text_content,created_at) VALUES(?,1,?,?,?,?)",
                    id, title, sourceLang, textContent, now);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "ACTIVE_CONTENT_EXISTS", "Điểm tham quan đã có nội dung đang hoạt động");
        }
        insertOutbox("content.updated", Map.of("contentId", id, "version", 1), correlationId, now);
        return content(id, poiId, 1, title, sourceLang, textContent, false, createdBy, now, now);
    }

    public Map<String, Object> listContents(int page, int pageSize, String query, String poiId) {
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("""
                SELECT c.content_id,c.poi_id,c.current_version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,p.name AS poi_name
                  FROM contents c JOIN points_of_interest p ON p.poi_id=c.poi_id AND p.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (poiId != null && !poiId.isBlank()) {
            sql.append(" AND c.poi_id=?");
            args.add(poiId);
        }
        sql.append(" ORDER BY c.updated_at DESC");
        List<Map<String, Object>> rows = jdbc.query(sql.toString(), (rs, r) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("contentId", rs.getString("content_id"));
            item.put("poiId", rs.getString("poi_id"));
            item.put("poiName", rs.getString("poi_name"));
            item.put("title", rs.getString("title"));
            item.put("sourceLang", rs.getString("source_lang"));
            item.put("currentVersion", rs.getInt("current_version"));
            item.put("isDeleted", rs.getBoolean("is_deleted"));
            item.put("createdAt", rs.getString("created_at"));
            item.put("updatedAt", rs.getString("updated_at"));
            return item;
        }, args.toArray());

        if (query != null && !query.isBlank()) {
            String q = LanguageRegistry.unaccent(query);
            rows = rows.stream().filter(c -> LanguageRegistry.unaccent((String) c.get("title")).contains(q)
                    || LanguageRegistry.unaccent((String) c.get("poiName")).contains(q)).toList();
        }
        return page(rows, page, pageSize);
    }

    public Map<String, Object> activeContentByPoi(String poiId) {
        return findActiveContent(poiId).orElseThrow(this::contentMissing);
    }

    public Map<String, Object> getContent(String contentId) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.content_id,c.poi_id,c.current_version AS version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,v.text_content
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.content_id=? AND c.is_deleted=0
                """, this::content, contentId);
        return list.stream().findFirst().orElseThrow(this::contentMissing);
    }

    @Transactional
    public Map<String, Object> updateContent(String contentId, int baseVersion, String title, String sourceLang, String textContent, String correlationId) {
        validateContent(title, sourceLang, textContent);
        lockContent(contentId);
        List<Map<String, Object>> rows = jdbc.query("SELECT current_version,poi_id,is_deleted FROM contents WHERE content_id=?",
                (rs, r) -> Map.of("version", rs.getInt("current_version"), "poiId", rs.getString("poi_id"), "isDeleted", rs.getBoolean("is_deleted")),
                contentId);
        if (rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("isDeleted"))) throw contentMissing();
        int currentVersion = (int) rows.get(0).get("version");
        if (baseVersion != currentVersion)
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_VERSION_CONFLICT", "Nội dung đã được cập nhật bởi thao tác khác",
                    Map.of("currentVersion", currentVersion));
        int next = currentVersion + 1;
        String now = clock.instant().toString();
        jdbc.update("INSERT INTO content_versions(content_id,version,title,source_lang,text_content,created_at) VALUES(?,?,?,?,?,?)",
                contentId, next, title, sourceLang, textContent, now);
        jdbc.update("UPDATE contents SET current_version=?,updated_at=? WHERE content_id=?", next, now, contentId);
        insertOutbox("content.updated", Map.of("contentId", contentId, "version", next), correlationId, now);
        return content(contentId, (String) rows.get(0).get("poiId"), next, title, sourceLang, textContent, false, null, null, now);
    }

    @Transactional
    public void deleteContent(String contentId, String correlationId) {
        lockContent(contentId);
        List<Map<String, Object>> rows = jdbc.query("SELECT poi_id,current_version,is_deleted FROM contents WHERE content_id=?",
                (rs, r) -> Map.of("poiId", rs.getString("poi_id"), "version", rs.getInt("current_version"), "isDeleted", rs.getBoolean("is_deleted")),
                contentId);
        if (rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).get("isDeleted"))) throw contentMissing();
        String now = clock.instant().toString();
        jdbc.update("UPDATE contents SET is_deleted=1,updated_at=? WHERE content_id=?", now, contentId);
        insertOutbox("content.deleted", Map.of("contentId", contentId, "poiId", rows.get(0).get("poiId")), correlationId, now);
    }

    public Map<String, Object> exactVersion(String contentId, int version) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.content_id,c.poi_id,v.version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,v.text_content
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id AND v.version=?
                 WHERE c.content_id=?
                """, this::content, version, contentId);
        return list.stream().findFirst().orElseThrow(this::contentMissing);
    }

    public Map<String, Object> currentForJob(String contentId) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.content_id,c.current_version AS version,v.source_lang
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.content_id=? AND c.is_deleted=0
                """, (rs, r) -> Map.of("contentId", rs.getString("content_id"), "version", rs.getInt("version"),
                "sourceLang", rs.getString("source_lang")), contentId);
        return list.stream().findFirst().orElseThrow(this::contentMissing);
    }

    public Map<String, Object> listVisitorPois(int page, int pageSize, String query) {
        validatePage(page, pageSize);
        List<Map<String, Object>> all = jdbc.query("""
                SELECT p.poi_id,p.name,p.description,p.latitude,p.longitude,p.trigger_radius,p.qr_code
                  FROM points_of_interest p
                 WHERE p.is_deleted=0
                   AND EXISTS (SELECT 1 FROM contents c WHERE c.poi_id=p.poi_id AND c.is_deleted=0)
                ORDER BY p.name
                """, (rs, r) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("poiId", rs.getString("poi_id"));
            m.put("name", rs.getString("name"));
            m.put("description", rs.getString("description"));
            double lat = rs.getDouble("latitude");
            if (!rs.wasNull()) m.put("latitude", lat);
            double lng = rs.getDouble("longitude");
            if (!rs.wasNull()) m.put("longitude", lng);
            int rRadius = rs.getInt("trigger_radius");
            m.put("triggerRadius", rs.wasNull() ? 30 : rRadius);
            m.put("qrCode", rs.getString("qr_code"));
            return m;
        });

        if (query != null && !query.isBlank()) {
            String q = LanguageRegistry.unaccent(query);
            all = all.stream().filter(p -> LanguageRegistry.unaccent((String) p.get("name")).contains(q)).toList();
        }
        return page(all, page, pageSize);
    }

    public Map<String, Object> visitorPoiCurrent(String poiId) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT p.poi_id,p.name AS poi_name,c.content_id,v.title AS content_title
                  FROM points_of_interest p
                  JOIN contents c ON c.poi_id=p.poi_id AND c.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE p.poi_id=? AND p.is_deleted=0
                """, (rs, r) -> Map.of("poiId", rs.getString("poi_id"), "poiName", rs.getString("poi_name"),
                "contentId", rs.getString("content_id"), "contentTitle", rs.getString("content_title")), poiId);
        return list.stream().findFirst().orElseThrow(this::poiMissing);
    }

    public Map<String, Object> visitorPoiByQr(String qrCode) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT p.poi_id,p.name AS poi_name,c.content_id,v.title AS content_title
                  FROM points_of_interest p
                  JOIN contents c ON c.poi_id=p.poi_id AND c.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE p.qr_code=? AND p.is_deleted=0
                """, (rs, r) -> Map.of("poiId", rs.getString("poi_id"), "poiName", rs.getString("poi_name"),
                "contentId", rs.getString("content_id"), "contentTitle", rs.getString("content_title")), qrCode);
        return list.stream().findFirst().orElseThrow(this::poiMissing);
    }

    public Map<String, Object> contentsLookup() {
        List<Map<String, Object>> items = jdbc.query("""
                SELECT c.content_id, c.poi_id, p.name AS poi_name, v.title
                  FROM contents c
                  JOIN points_of_interest p ON p.poi_id=c.poi_id AND p.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.is_deleted=0
                ORDER BY p.name
                """, (rs, r) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contentId", rs.getString("content_id"));
            m.put("poiId", rs.getString("poi_id"));
            m.put("poiName", rs.getString("poi_name"));
            m.put("title", rs.getString("title"));
            return m;
        });
        return Map.of("items", items);
    }

    private void lockContent(String id) {
        jdbc.update("UPDATE contents SET updated_at=updated_at WHERE content_id=? AND is_deleted=0", id);
    }

    private Optional<Map<String, Object>> findActiveContent(String poiId) {
        List<Map<String, Object>> list = jdbc.query("""
                SELECT c.content_id,c.poi_id,c.current_version AS version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,v.text_content
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.poi_id=? AND c.is_deleted=0
                """, this::content, poiId);
        return list.stream().findFirst();
    }

    private void lockPoi(String poiId) {
        jdbc.update("UPDATE points_of_interest SET updated_at=updated_at WHERE poi_id=? AND is_deleted=0", poiId);
    }

    private Optional<Map<String, Object>> findPoi(String id) {
        List<Map<String, Object>> result = jdbc.query("SELECT * FROM points_of_interest WHERE poi_id=? AND is_deleted=0", this::poiRow, id);
        return result.stream().findFirst();
    }

    private Map<String, Object> poiRow(ResultSet rs, int row) throws SQLException {
        double lat = rs.getDouble("latitude");
        Double latitude = rs.wasNull() ? null : lat;
        double lng = rs.getDouble("longitude");
        Double longitude = rs.wasNull() ? null : lng;
        int rad = rs.getInt("trigger_radius");
        Integer triggerRadius = rs.wasNull() ? 30 : rad;

        return poi(rs.getString("poi_id"), rs.getString("name"), rs.getString("description"), rs.getString("qr_code"),
                latitude, longitude, triggerRadius, rs.getString("created_at"), rs.getString("updated_at"));
    }

    private Map<String, Object> poi(String id, String name, String description, String qr, Double latitude, Double longitude, Integer triggerRadius, String created, String updated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poiId", id);
        result.put("name", name);
        result.put("description", description);
        result.put("qrCode", qr);
        if (latitude != null) result.put("latitude", latitude);
        if (longitude != null) result.put("longitude", longitude);
        result.put("triggerRadius", triggerRadius != null ? triggerRadius : 30);
        result.put("createdAt", created);
        result.put("updatedAt", updated);
        return result;
    }

    private Map<String, Object> content(ResultSet rs, int row) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentId", rs.getString("content_id"));
        result.put("poiId", rs.getString("poi_id"));
        result.put("version", rs.getInt("version"));
        result.put("title", rs.getString("title"));
        result.put("sourceLang", rs.getString("source_lang"));
        result.put("textContent", rs.getString("text_content"));
        result.put("isDeleted", rs.getBoolean("is_deleted"));
        result.put("createdBy", rs.getString("created_by"));
        result.put("createdAt", rs.getString("created_at"));
        result.put("updatedAt", rs.getString("updated_at"));
        return result;
    }

    private Map<String, Object> content(String id, String poi, int version, String title, String lang, String text, boolean deleted, String by, String created, String updated) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contentId", id);
        result.put("poiId", poi);
        result.put("version", version);
        result.put("title", title);
        result.put("sourceLang", lang);
        result.put("textContent", text);
        result.put("isDeleted", deleted);
        result.put("createdBy", by);
        result.put("createdAt", created);
        result.put("updatedAt", updated);
        return result;
    }

    private void insertOutbox(String route, Map<String, Object> payload, String correlationId, String now) {
        try {
            jdbc.update("INSERT INTO outbox_events(event_id,routing_key,payload,correlation_id,occurred_at,published) VALUES(?,?,?,?,?,0)",
                    UUID.randomUUID().toString(), route, json.writeValueAsString(payload), correlationId, now);
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể ghi sự kiện outbox", ex);
        }
    }

    private Map<String, Object> page(List<Map<String, Object>> all, int page, int pageSize) {
        int start = Math.min((page - 1) * pageSize, all.size()), end = Math.min(start + pageSize, all.size());
        return Map.of("items", all.subList(start, end), "page", page, "pageSize", pageSize, "totalItems", all.size(),
                "totalPages", all.isEmpty() ? 0 : (all.size() + pageSize - 1) / pageSize);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
    }

    private void validatePoi(String name, String description, Double latitude, Double longitude) {
        if (name == null || name.isBlank() || name.length() > 255 || description != null && description.length() > 2000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tên điểm bắt buộc, tối đa 255 ký tự; mô tả tối đa 2.000 ký tự",
                    Map.of("name", "required, max 255", "description", "max 2000"));
        if (latitude != null && (latitude < -90.0 || latitude > 90.0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES", "Vĩ độ không hợp lệ, phạm vi từ -90 đến +90");
        }
        if (longitude != null && (longitude < -180.0 || longitude > 180.0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES", "Kinh độ không hợp lệ, phạm vi từ -180 đến +180");
        }
    }

    private void validateContent(String title, String lang, String text) {
        if (title == null || title.isBlank() || title.length() > 255 || text == null || text.isBlank() || text.length() > 10000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu nội dung không hợp lệ",
                    Map.of("title", "required, max 255", "textContent", "required, max 10000"));
        if (!LanguageRegistry.isSupported(lang)) {
            var def = LanguageRegistry.findByCode(lang);
            String msg = (def != null && !def.enabled())
                    ? "Ngôn ngữ nguồn '" + lang + "' đã tạm dừng hoạt động hoặc chưa được kích hoạt"
                    : "Ngôn ngữ nguồn '" + lang + "' không được hệ thống hỗ trợ";
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SOURCE_LANGUAGE", msg,
                    Map.of("sourceLang", lang == null ? "null" : lang));
        }
    }

    private double calculateHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Radius of the Earth in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String newQrCode() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiException poiMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, "POI_NOT_FOUND", "Không tìm thấy điểm tham quan");
    }

    private ApiException contentMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "Không tìm thấy nội dung");
    }
}
