package vn.edu.cnpm.narration.api;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.narration.service.NarrationCoordinator;
import vn.edu.cnpm.narration.service.NarrationStore;
import java.util.Map;
@RestController
public class NarrationController {
    private final NarrationStore store;private final NarrationCoordinator coordinator;
    public NarrationController(NarrationStore store,NarrationCoordinator coordinator){this.store=store;this.coordinator=coordinator;}
    @GetMapping("/api/v1/voices")
    public ApiEnvelope<Map<String,Object>> voices(){return ApiEnvelope.success(coordinator.voices());}
    @GetMapping("/api/v1/jobs")
    public ApiEnvelope<Map<String,Object>> list(
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize,
            @RequestParam(required=false) String poiId,
            @RequestParam(required=false) String status,
            @RequestParam(required=false) String dateRange,
            @RequestParam(required=false) String lang,
            @RequestParam(required=false) String query) {
        return ApiEnvelope.success(coordinator.listJobs(page, pageSize, poiId, status, dateRange, lang, query));
    }
    @PostMapping({"/api/v1/jobs", "/internal/jobs"})
    public ResponseEntity<ApiEnvelope<Map<String,Object>>> create(@RequestHeader(value="Idempotency-Key",required=false) String key,
            @RequestBody CreateJobRequest body,HttpServletRequest request) {
        Map<String,Object> job=coordinator.createJob(body==null?null:body.contentId(),body==null?null:body.retryOfJobId(),
                body==null?null:body.expectedVersion(),
                body==null?null:body.targets(),key,request.getHeader("X-User-Id"),request.getHeader("X-Correlation-ID"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.success(job));
    }
    @GetMapping("/api/v1/jobs/{jobId}")
    public ApiEnvelope<Map<String,Object>> get(@PathVariable String jobId){return ApiEnvelope.success(store.getJob(jobId));}
    @PostMapping("/api/v1/jobs/{jobId}/cancel")
    public ApiEnvelope<Map<String,Object>> cancel(@PathVariable String jobId){return ApiEnvelope.success(store.cancelJob(jobId));}

    @PostMapping("/api/v1/jobs/batch-translate")
    public ResponseEntity<ApiEnvelope<Map<String,Object>>> startBatchTranslate(@RequestBody BatchTranslateRequest body,
                                                                              HttpServletRequest request) {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Thiếu danh tính đã xác thực");
        Map<String,Object> result = coordinator.startBatchTranslate(body == null ? null : body.targetLang(),
                body == null ? null : body.voiceId(), userId, request.getHeader("X-Correlation-ID"));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiEnvelope.success(result));
    }

    @GetMapping("/api/v1/jobs/batch-translate/{batchId}")
    public ApiEnvelope<Map<String,Object>> getBatchTranslate(@PathVariable String batchId) {
        return ApiEnvelope.success(coordinator.getBatchTranslate(batchId));
    }

    @PostMapping("/api/v1/jobs/batch-translate/{batchId}/cancel")
    public ApiEnvelope<Map<String,Object>> cancelBatchTranslate(@PathVariable String batchId) {
        return ApiEnvelope.success(coordinator.cancelBatchTranslate(batchId));
    }

    @GetMapping("/internal/narrations")
    public ApiEnvelope<Map<String,Object>> listPublished(@RequestParam String contentId){
        return ApiEnvelope.success(coordinator.publishedLanguages(contentId));
    }
    @GetMapping("/internal/narrations/{lang}")
    public ApiEnvelope<Map<String,Object>> published(@PathVariable String lang,@RequestParam String contentId,HttpServletRequest request){
        return ApiEnvelope.success(coordinator.publishedNarration(contentId,lang,request.getHeader("X-Correlation-ID")));
    }
    public record CreateJobRequest(String contentId,String retryOfJobId,Integer expectedVersion,java.util.List<NarrationCoordinator.TargetRequest> targets){}
    public record BatchTranslateRequest(String targetLang, String voiceId){}
}
