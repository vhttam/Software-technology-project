package vn.edu.cnpm.content.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.content.service.ContentService;
import java.util.Map;

@RestController
public class ContentController {
    private final ContentService content;
    public ContentController(ContentService content) { this.content=content; }

    @GetMapping("/api/v1/languages/source")
    public ApiEnvelope<Map<String,Object>> languages() { return ApiEnvelope.success(content.sourceLanguages()); }

    @GetMapping("/api/v1/languages/config")
    public ApiEnvelope<Map<String,Object>> languageConfig(
            @RequestParam(required=false, defaultValue="false") boolean all,
            @RequestParam(required=false, defaultValue="false") boolean includeDisabled) {
        var list = (all || includeDisabled)
                ? vn.edu.cnpm.content.config.LanguageRegistry.getAllLanguages()
                : vn.edu.cnpm.content.config.LanguageRegistry.getSupportedLanguages();
        return ApiEnvelope.success(Map.of("languages", list));
    }

    @GetMapping("/api/v1/pois")
    public ApiEnvelope<Map<String,Object>> pois(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String query) {
        return ApiEnvelope.success(content.listPois(page,pageSize,query));
    }
    @PostMapping("/api/v1/pois")
    public ResponseEntity<ApiEnvelope<Map<String,Object>>> createPoi(@Valid @RequestBody PoiRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(content.createPoi(body.name(),body.description())));
    }
    @GetMapping("/api/v1/pois/{poiId}")
    public ApiEnvelope<Map<String,Object>> getPoi(@PathVariable String poiId) { return ApiEnvelope.success(content.getPoi(poiId)); }
    @PutMapping("/api/v1/pois/{poiId}")
    public ApiEnvelope<Map<String,Object>> updatePoi(@PathVariable String poiId,@Valid @RequestBody PoiRequest body) {
        return ApiEnvelope.success(content.updatePoi(poiId,body.name(),body.description()));
    }
    @DeleteMapping("/api/v1/pois/{poiId}")
    public ResponseEntity<Void> deletePoi(@PathVariable String poiId) { content.deletePoi(poiId);return ResponseEntity.noContent().build(); }

    @PostMapping("/api/v1/pois/{poiId}/contents")
    public ResponseEntity<ApiEnvelope<Map<String,Object>>> createContent(@PathVariable String poiId,@Valid @RequestBody ContentRequest body,
            HttpServletRequest request) {
        String creator=request.getHeader("X-User-Id");
        if(creator==null||creator.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Thiếu danh tính đã xác thực");
        var result=content.createContent(poiId,body.title(),body.sourceLang(),body.textContent(),creator,request.getHeader("X-Correlation-ID"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(result));
    }
    @GetMapping("/api/v1/contents")
    public ApiEnvelope<Map<String,Object>> contents(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String query,
            @RequestParam(required=false) String poiId) {
        return ApiEnvelope.success(content.listContents(page,pageSize,query,poiId));
    }
    @GetMapping("/api/v1/pois/{poiId}/contents/active")
    public ApiEnvelope<Map<String,Object>> active(@PathVariable String poiId) { return ApiEnvelope.success(content.activeContentByPoi(poiId)); }
    @GetMapping("/api/v1/contents/{contentId}")
    public ApiEnvelope<Map<String,Object>> getContent(@PathVariable String contentId) { return ApiEnvelope.success(content.getContent(contentId)); }
    @PutMapping("/api/v1/contents/{contentId}")
    public ApiEnvelope<Map<String,Object>> updateContent(@PathVariable String contentId,@Valid @RequestBody UpdateContentRequest body,
            HttpServletRequest request) {
        return ApiEnvelope.success(content.updateContent(contentId,body.baseVersion(),body.title(),body.sourceLang(),
                body.textContent(),request.getHeader("X-Correlation-ID")));
    }
    @DeleteMapping("/api/v1/contents/{contentId}")
    public ResponseEntity<Void> deleteContent(@PathVariable String contentId,HttpServletRequest request) {
        content.deleteContent(contentId,request.getHeader("X-Correlation-ID"));return ResponseEntity.noContent().build();
    }
    @GetMapping("/internal/contents/{contentId}/versions/{version}")
    public ApiEnvelope<Map<String,Object>> exactVersion(@PathVariable String contentId,@PathVariable int version) {
        return ApiEnvelope.success(content.exactVersion(contentId,version));
    }
    @GetMapping("/internal/contents/{contentId}/current")
    public ApiEnvelope<Map<String,Object>> current(@PathVariable String contentId) { return ApiEnvelope.success(content.currentForJob(contentId)); }
    @GetMapping("/internal/pois")
    public ApiEnvelope<Map<String,Object>> visitorPois(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String query) {
        return ApiEnvelope.success(content.listVisitorPois(page,pageSize,query));
    }
    @GetMapping("/internal/pois/{poiId}/current")
    public ApiEnvelope<Map<String,Object>> visitorPoi(@PathVariable String poiId) { return ApiEnvelope.success(content.visitorPoiCurrent(poiId)); }
    @GetMapping("/internal/pois/by-qr/{qrCode}")
    public ApiEnvelope<Map<String,Object>> visitorQr(@PathVariable String qrCode) { return ApiEnvelope.success(content.visitorPoiByQr(qrCode)); }
    @GetMapping("/internal/contents/lookup")
    public ApiEnvelope<Map<String,Object>> lookup() { return ApiEnvelope.success(content.contentsLookup()); }

    public record PoiRequest(@NotBlank @Size(max=255) String name,@Size(max=2000) String description) {}
    public record ContentRequest(@NotBlank @Size(max=255) String title,@NotBlank String sourceLang,@NotBlank @Size(max=10000) String textContent) {}
    public record UpdateContentRequest(int baseVersion,@NotBlank @Size(max=255) String title,@NotBlank String sourceLang,
                                      @NotBlank @Size(max=10000) String textContent) {}
}
