package vn.edu.cnpm.content.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.content.config.LanguageRegistry;
import vn.edu.cnpm.content.service.ContentService;
import vn.edu.cnpm.content.service.PresenceService;
import vn.edu.cnpm.content.service.QrCodeGeneratorService;

import java.util.List;
import java.util.Map;

@RestController
public class ContentController {
    private final ContentService content;
    private final PresenceService presence;
    private final QrCodeGeneratorService qrGenerator;

    public ContentController(ContentService content, PresenceService presence, QrCodeGeneratorService qrGenerator) {
        this.content = content;
        this.presence = presence;
        this.qrGenerator = qrGenerator;
    }

    @GetMapping("/api/v1/languages/source")
    public ApiEnvelope<Map<String, Object>> languages() {
        return ApiEnvelope.success(content.sourceLanguages());
    }

    @GetMapping("/api/v1/languages/config")
    public ApiEnvelope<Map<String, Object>> languageConfig(
            @RequestParam(required = false, defaultValue = "false") boolean all,
            @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        var list = (all || includeDisabled)
                ? LanguageRegistry.getAllLanguages()
                : LanguageRegistry.getSupportedLanguages();
        return ApiEnvelope.success(Map.of("languages", list));
    }

    // ==========================================
    // POI APIs
    // ==========================================
    @GetMapping("/api/v1/pois")
    public ApiEnvelope<Map<String, Object>> pois(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String query) {
        return ApiEnvelope.success(content.listPois(page, pageSize, query));
    }

    @PostMapping("/api/v1/pois")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createPoi(@Valid @RequestBody PoiRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
                content.createPoi(body.name(), body.description(), body.latitude(), body.longitude(), body.triggerRadius())));
    }

    @GetMapping("/api/v1/pois/{poiId}")
    public ApiEnvelope<Map<String, Object>> getPoi(@PathVariable String poiId) {
        return ApiEnvelope.success(content.getPoi(poiId));
    }

    @PutMapping("/api/v1/pois/{poiId}")
    public ApiEnvelope<Map<String, Object>> updatePoi(@PathVariable String poiId, @Valid @RequestBody PoiRequest body) {
        return ApiEnvelope.success(content.updatePoi(poiId, body.name(), body.description(), body.latitude(), body.longitude(), body.triggerRadius()));
    }

    @DeleteMapping("/api/v1/pois/{poiId}")
    public ResponseEntity<Void> deletePoi(@PathVariable String poiId) {
        content.deletePoi(poiId);
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Quick Create with Job (UC8 / BR-22)
    // ==========================================
    @PostMapping("/api/v1/pois/quick-create-with-job")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> quickCreateWithJob(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody QuickCreateRequest body,
            HttpServletRequest request) {

        String creator = request.getHeader("X-User-Id");
        String correlationId = request.getHeader("X-Correlation-ID");

        List<Map<String, String>> targets = body.job() != null && body.job().targets() != null
                ? body.job().targets().stream().map(t -> Map.of("lang", t.lang(), "voiceId", t.voiceId() != null ? t.voiceId() : "")).toList()
                : List.of();

        Map<String, Object> result = content.quickCreateWithJob(
                body.poi().name(), body.poi().description(), body.poi().latitude(), body.poi().longitude(), body.poi().triggerRadius(),
                body.content().title(), body.content().sourceLang(), body.content().textContent(),
                targets,
                idempotencyKey, creator, correlationId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(result));
    }

    // ==========================================
    // QR Code Image (UC13 / BR-20)
    // ==========================================
    @GetMapping(value = {"/api/v1/pois/{poiId}/qr-image", "/api/v1/client/pois/{poiId}/qr-image"})
    public ResponseEntity<?> qrImage(
            @PathVariable String poiId,
            @RequestParam(defaultValue = "png") String format,
            @RequestParam(defaultValue = "600") int size) {
        Map<String, Object> poi = content.getPoi(poiId);
        String qrCode = (String) poi.get("qrCode");

        if ("svg".equalsIgnoreCase(format)) {
            String svg = qrGenerator.generateSvg(qrCode, size);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "image/svg+xml;charset=UTF-8")
                    .body(svg);
        } else {
            byte[] png = qrGenerator.generatePng(qrCode, size);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE)
                    .body(png);
        }
    }

    // ==========================================
    // Client POI Card & Nearby (UC11, UC12, UC14)
    // ==========================================
    @GetMapping({"/api/v1/client/pois/{poiId}/card", "/internal/pois/{poiId}/card"})
    public ApiEnvelope<Map<String, Object>> poiCard(@PathVariable String poiId) {
        return ApiEnvelope.success(content.getPoiCard(poiId));
    }

    @GetMapping({"/api/v1/client/pois/nearby", "/internal/pois/nearby"})
    public ApiEnvelope<Map<String, Object>> nearbyPois(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "1500") double radius) {
        return ApiEnvelope.success(content.listNearbyPois(lat, lng, radius));
    }

    // ==========================================
    // Presence Tracking (UC10)
    // ==========================================
    @PostMapping("/api/v1/client/presence/heartbeat")
    public ApiEnvelope<Map<String, Object>> heartbeat(@RequestBody(required = false) HeartbeatRequest body) {
        String clientId = body != null && body.clientId() != null ? body.clientId() : (body != null ? body.visitorId() : null);
        Boolean isListening = body != null ? body.isListening() : false;
        String poiId = body != null ? body.poiId() : null;
        String lang = body != null ? body.lang() : null;
        Boolean leave = body != null && Boolean.TRUE.equals(body.leave());
        return ApiEnvelope.success(presence.heartbeat(clientId, isListening, poiId, lang, leave));
    }

    @GetMapping("/api/v1/client/presence/online-count")
    public ApiEnvelope<Map<String, Object>> onlineCount() {
        return ApiEnvelope.success(presence.getOnlineCount());
    }

    @GetMapping("/api/v1/presence/metrics")
    public ApiEnvelope<Map<String, Object>> presenceMetrics() {
        return ApiEnvelope.success(presence.getMetrics());
    }

    // ==========================================
    // Content Management (UC1)
    // ==========================================
    @PostMapping("/api/v1/pois/{poiId}/contents")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createContent(
            @PathVariable String poiId, @Valid @RequestBody ContentRequest body, HttpServletRequest request) {
        String creator = request.getHeader("X-User-Id");
        if (creator == null || creator.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu danh tính đã xác thực");
        var result = content.createContent(poiId, body.title(), body.sourceLang(), body.textContent(), creator, request.getHeader("X-Correlation-ID"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(result));
    }

    @GetMapping("/api/v1/contents")
    public ApiEnvelope<Map<String, Object>> contents(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String poiId) {
        return ApiEnvelope.success(content.listContents(page, pageSize, query, poiId));
    }

    @GetMapping("/api/v1/pois/{poiId}/contents/active")
    public ApiEnvelope<Map<String, Object>> active(@PathVariable String poiId) {
        return ApiEnvelope.success(content.activeContentByPoi(poiId));
    }

    @GetMapping("/api/v1/contents/{contentId}")
    public ApiEnvelope<Map<String, Object>> getContent(@PathVariable String contentId) {
        return ApiEnvelope.success(content.getContent(contentId));
    }

    @PutMapping("/api/v1/contents/{contentId}")
    public ApiEnvelope<Map<String, Object>> updateContent(
            @PathVariable String contentId, @Valid @RequestBody UpdateContentRequest body, HttpServletRequest request) {
        return ApiEnvelope.success(content.updateContent(contentId, body.baseVersion(), body.title(), body.sourceLang(),
                body.textContent(), request.getHeader("X-Correlation-ID")));
    }

    @DeleteMapping("/api/v1/contents/{contentId}")
    public ResponseEntity<Void> deleteContent(@PathVariable String contentId, HttpServletRequest request) {
        content.deleteContent(contentId, request.getHeader("X-Correlation-ID"));
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // Internal Service APIs
    // ==========================================
    @GetMapping("/internal/contents/{contentId}/versions/{version}")
    public ApiEnvelope<Map<String, Object>> exactVersion(@PathVariable String contentId, @PathVariable int version) {
        return ApiEnvelope.success(content.exactVersion(contentId, version));
    }

    @GetMapping("/internal/contents/{contentId}/current")
    public ApiEnvelope<Map<String, Object>> current(@PathVariable String contentId) {
        return ApiEnvelope.success(content.currentForJob(contentId));
    }

    @GetMapping("/internal/pois")
    public ApiEnvelope<Map<String, Object>> visitorPois(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String query) {
        return ApiEnvelope.success(content.listVisitorPois(page, pageSize, query));
    }

    @GetMapping("/internal/pois/{poiId}/current")
    public ApiEnvelope<Map<String, Object>> visitorPoi(@PathVariable String poiId) {
        return ApiEnvelope.success(content.visitorPoiCurrent(poiId));
    }

    @GetMapping("/internal/pois/by-qr/{qrCode}")
    public ApiEnvelope<Map<String, Object>> visitorQr(@PathVariable String qrCode) {
        return ApiEnvelope.success(content.visitorPoiByQr(qrCode));
    }

    @GetMapping("/internal/contents/lookup")
    public ApiEnvelope<Map<String, Object>> lookup() {
        return ApiEnvelope.success(content.contentsLookup());
    }

    // ==========================================
    // Request Records
    // ==========================================
    public record PoiRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 2000) String description,
            Double latitude,
            Double longitude,
            Integer triggerRadius
    ) {}

    public record ContentRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank String sourceLang,
            @NotBlank @Size(max = 10000) String textContent
    ) {}

    public record UpdateContentRequest(
            int baseVersion,
            @NotBlank @Size(max = 255) String title,
            @NotBlank String sourceLang,
            @NotBlank @Size(max = 10000) String textContent
    ) {}

    public record QuickCreateRequest(
            @NotNull PoiRequest poi,
            @NotNull ContentRequest content,
            @NotNull JobRequest job
    ) {}

    public record JobRequest(List<TargetRequest> targets) {}

    public record TargetRequest(String lang, String voiceId) {}

    public record HeartbeatRequest(String clientId, String visitorId, Boolean isListening, String poiId, String lang, Boolean leave) {}
}
