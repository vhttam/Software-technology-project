package vn.edu.cnpm.narration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import vn.edu.cnpm.narration.api.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NarrationCoordinator {
    private final NarrationStore store;private final RestClient content;private final RestClient tts;
    private final RestClient translation;private final String serviceToken;private final ObjectMapper mapper;
    public NarrationCoordinator(NarrationStore store,Environment env,ObjectMapper mapper) {
        this.store=store;this.mapper=mapper;
        content=RestClient.builder().baseUrl(env.getProperty("CONTENT_SERVICE_URL","http://localhost:8082")).build();
        tts=RestClient.builder().baseUrl(env.getProperty("TTS_SERVICE_URL","http://localhost:8085")).build();
        translation=RestClient.builder().baseUrl(env.getProperty("TRANSLATION_SERVICE_URL","http://localhost:8084")).build();
        serviceToken=env.getProperty("NARRATION_SERVICE_TOKEN","");
    }

    public Map<String,Object> voices() {
        try {
            Map<?,?> response=tts.get().uri("/internal/voices").header("X-Service-Token",serviceToken)
                    .retrieve().body(Map.class);
            if(response==null||!(response.get("data") instanceof Map<?,?> data))throw unavailable("TTS");
            return (Map<String,Object>)data;
        } catch(RestClientResponseException ex){throw unavailable("TTS");}
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> createJob(String contentId,String retryOfJobId,Integer expectedVersion,List<TargetRequest> targets,
                                        String idempotencyKey,String userId,String correlationId) {
        if(idempotencyKey==null||idempotencyKey.isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED","Thiếu Idempotency-Key");
        if(idempotencyKey.length()>255)
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","Idempotency-Key dài quá 255 ký tự");
        if(contentId==null||contentId.isBlank()||targets==null||targets.isEmpty())
            throw invalidTargets();
        if(userId==null||userId.isBlank())throw new ApiException(HttpStatus.UNAUTHORIZED,"INVALID_CREDENTIALS","Thiếu danh tính đã xác thực");
        String requestHash=hash(contentId,retryOfJobId,targets);
        Map<String,Object> old=store.findIdempotent(userId,idempotencyKey,requestHash);
        if(old!=null)return old;

        Map<String,Object> current=readCurrentContent(contentId,correlationId);
        int version=((Number)current.get("version")).intValue();
        if (expectedVersion != null && expectedVersion.intValue() != version) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Nội dung đã được cập nhật phiên bản mới, vui lòng tải lại trang",
                    Map.of("currentVersion", version, "expectedVersion", expectedVersion));
        }

        Map<String,Object> supported=voices();
        Object voiceRows=supported.get("supportedVoices");
        if(!(voiceRows instanceof List<?> voiceList))throw invalidTargets();
        List<Map<String,String>> normalized=new ArrayList<>();
        java.util.Set<String> seen=new java.util.HashSet<>();
        for(TargetRequest target:targets) {
            if(target==null||blank(target.lang())||blank(target.voiceId())||!seen.add(target.lang())) {
                throw invalidTargets();
            }
            boolean langSupported = voiceList.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .anyMatch(row -> target.lang().equalsIgnoreCase(String.valueOf(row.get("lang"))));
            if (!langSupported) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LANGUAGE_NOT_ENABLED",
                        "Ngôn ngữ '" + target.lang() + "' chưa được kích hoạt hoặc không khả dụng trên hệ thống");
            }
            boolean voiceExists=voiceList.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .filter(row->target.lang().equalsIgnoreCase(String.valueOf(row.get("lang"))))
                    .anyMatch(row->row.get("voices") instanceof List<?> list&&list.contains(target.voiceId()));
            if(!voiceExists) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VOICE",
                        "Giọng đọc '" + target.voiceId() + "' không hợp lệ cho ngôn ngữ " + target.lang());
            }
            normalized.add(Map.of("lang",target.lang(),"voiceId",target.voiceId()));
        }
        List<NarrationStore.JobTargetInput> inputs=normalized.stream()
                .map(m->new NarrationStore.JobTargetInput(m.get("lang"),m.get("voiceId"))).toList();
        return store.createJob(userId,idempotencyKey,requestHash,contentId,version,inputs,retryOfJobId,correlationId);
    }

    public Map<String,Object> createJob(String contentId,String retryOfJobId,List<TargetRequest> targets,
                                        String idempotencyKey,String userId,String correlationId) {
        return createJob(contentId, retryOfJobId, null, targets, idempotencyKey, userId, correlationId);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> readCurrentContent(String contentId,String correlationId) {
        try {
            Map<String,Object> envelope=content.get().uri("/internal/contents/{id}/current",contentId)
                    .header("X-Service-Token",serviceToken).header("X-Correlation-ID",correlationId==null?"":correlationId)
                    .retrieve().body(Map.class);
            if(envelope==null||!(envelope.get("data") instanceof Map<?,?> data))throw unavailable("Content");
            return (Map<String,Object>)data;
        } catch(RestClientResponseException ex) {
            if(ex.getStatusCode().value()==404) {
                try {
                    Map<String,Object> exact=content.get().uri("/internal/contents/{id}/versions/1",contentId)
                            .header("X-Service-Token",serviceToken).header("X-Correlation-ID",correlationId==null?"":correlationId)
                            .retrieve().body(Map.class);
                    if(exact!=null&&exact.get("data") instanceof Map<?,?> exactData) {
                        if(Boolean.TRUE.equals(exactData.get("isDeleted"))) {
                            throw new ApiException(HttpStatus.BAD_REQUEST,"CONTENT_DELETED","Không thể tạo tác vụ cho nội dung đã bị xóa");
                        }
                    }
                } catch(ApiException aex) { throw aex; }
                catch(Exception ignored) {}
                throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy nội dung");
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTENT_SERVICE_UNAVAILABLE","Content Service tạm thời không khả dụng");
        } catch(ApiException ex){throw ex;}
        catch(Exception ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CONTENT_SERVICE_UNAVAILABLE","Content Service tạm thời không khả dụng");}
    }

    public Map<String,Object> publishedLanguages(String contentId) {
        List<Map<String,Object>> langs=store.publishedLanguages(contentId);
        int currentVersion = -1;
        try {
            Map<String, Object> current = readCurrentContent(contentId, null);
            if (current != null && current.get("version") instanceof Number num) {
                currentVersion = num.intValue();
            }
        } catch (Exception ignored) {}

        final int finalCurrentVersion = currentVersion;
        List<Map<String, Object>> enriched = langs.stream().map(item -> {
            Map<String, Object> map = new java.util.LinkedHashMap<>(item);
            int v = ((Number) item.get("version")).intValue();
            if (finalCurrentVersion > 0) {
                map.put("currentVersion", finalCurrentVersion);
                map.put("isOutdated", v < finalCurrentVersion);
            } else {
                map.put("isOutdated", false);
            }
            return map;
        }).toList();

        return Map.of("narrations", enriched);
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> listJobs(int page, int pageSize, String poiId, String status, String dateRange, String lang, String query) {
        if (page < 1 || pageSize < 1 || pageSize > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
        }

        Map<String, Map<String, Object>> contentsByContentId = new java.util.LinkedHashMap<>();
        try {
            Map<?, ?> resp = content.get().uri("/internal/contents/lookup")
                    .header("X-Service-Token", serviceToken)
                    .retrieve().body(Map.class);
            if (resp != null && resp.get("data") instanceof Map<?, ?> data && data.get("items") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        String cId = (String) m.get("contentId");
                        if (cId != null) {
                            contentsByContentId.put(cId, (Map<String, Object>) m);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // Content service unavailable or error; proceed with empty metadata
        }

        List<String> matchingContentIds = null;
        boolean hasPoiFilter = poiId != null && !poiId.isBlank();
        boolean hasQueryFilter = query != null && !query.isBlank();

        if (hasPoiFilter || hasQueryFilter) {
            String q = hasQueryFilter ? query.trim().toLowerCase() : null;
            matchingContentIds = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : contentsByContentId.entrySet()) {
                Map<String, Object> cMeta = entry.getValue();
                boolean matchesPoi = !hasPoiFilter || poiId.trim().equals(cMeta.get("poiId"));
                boolean matchesQuery = !hasQueryFilter || (
                        (cMeta.get("poiName") != null && ((String) cMeta.get("poiName")).toLowerCase().contains(q)) ||
                        (cMeta.get("title") != null && ((String) cMeta.get("title")).toLowerCase().contains(q))
                );
                if (matchesPoi && matchesQuery) {
                    matchingContentIds.add(entry.getKey());
                }
            }
        }

        Map<String, Object> result = store.listJobs(page, pageSize, status, lang, dateRange, matchingContentIds);
        Object rawItems = result.get("items");
        if (rawItems instanceof List<?> items) {
            for (Object obj : items) {
                if (obj instanceof Map<?, ?> rawJob) {
                    Map<String, Object> job = (Map<String, Object>) rawJob;
                    String cId = (String) job.get("contentId");
                    Map<String, Object> cMeta = contentsByContentId.get(cId);
                    job.put("contentTitle", cMeta != null && cMeta.get("title") != null ? cMeta.get("title") : "");
                    job.put("poiId", cMeta != null && cMeta.get("poiId") != null ? cMeta.get("poiId") : "");
                    job.put("poiName", cMeta != null && cMeta.get("poiName") != null ? cMeta.get("poiName") : "");
                }
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> publishedNarration(String contentId,String lang,String correlationId) {
        List<Map<String,Object>> langs=store.publishedLanguages(contentId);
        Map<String,Object> selected=store.publishedTarget(contentId,lang);
        if(selected==null)throw new ApiException(HttpStatus.NOT_FOUND,"NARRATION_NOT_FOUND",
                "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn",
                Map.of("availableLangs",langs.stream().map(item->item.get("lang")).toList()));
        String translationId=String.valueOf(selected.get("translationId"));
        String audioId=String.valueOf(selected.get("audioId"));
        try {
            Map<String,Object> translationResponse=translation.get().uri("/internal/translations/{id}",translationId)
                    .header("X-Service-Token",serviceToken).header("X-Correlation-ID",correlationId==null?"":correlationId)
                    .retrieve().body(Map.class);
            Map<String,Object> translationData=(Map<String,Object>)translationResponse.get("data");
            Map<String,Object> signedResponse=tts.get().uri("/internal/audios/{id}/signed-url",audioId)
                    .header("X-Service-Token",serviceToken).header("X-Correlation-ID",correlationId==null?"":correlationId)
                    .retrieve().body(Map.class);
            Map<String,Object> signedData=(Map<String,Object>)signedResponse.get("data");
            int currentVersion = -1;
            try {
                Map<String, Object> current = readCurrentContent(contentId, correlationId);
                if (current != null && current.get("version") instanceof Number num) {
                    currentVersion = num.intValue();
                }
            } catch (Exception ignored) {}
            int targetVer = ((Number) selected.get("version")).intValue();
            boolean isOutdated = currentVersion > 0 && targetVer < currentVersion;

            Map<String, Object> res = new java.util.LinkedHashMap<>();
            res.put("lang", lang);
            res.put("version", selected.get("version"));
            if (currentVersion > 0) res.put("currentVersion", currentVersion);
            res.put("isOutdated", isOutdated);
            res.put("subtitle", translationData.get("textContent"));
            res.put("audioUrl", signedData.get("audioUrl"));
            res.put("audioUrlExpiresAt", signedData.get("expiresAt"));
            return res;
        } catch(RestClientResponseException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"INTERNAL_ERROR","Dịch vụ nội bộ tạm thời không khả dụng");
        } catch(Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"INTERNAL_ERROR","Dịch vụ nội bộ tạm thời không khả dụng");
        }
    }

    private String hash(String contentId,String retryId,List<TargetRequest> targets) {
        try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(new CreatePayload(contentId,retryId,targets))));}
        catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private ApiException invalidTargets(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_TARGETS","Danh sách ngôn ngữ hoặc giọng đọc không hợp lệ");}
    private ApiException unavailable(String dependency){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"INTERNAL_ERROR",dependency+" Service tạm thời không khả dụng");}
    private boolean blank(String value){return value==null||value.isBlank();}
    public record TargetRequest(String lang,String voiceId){}
    private record CreatePayload(String contentId,String retryOfJobId,List<TargetRequest> targets){}

    public static class BatchJobItem {
        public final String contentId;
        public final int version;
        public final String poiName;
        public final String title;
        public BatchJobItem(String contentId, int version, String poiName, String title) {
            this.contentId = contentId;
            this.version = version;
            this.poiName = poiName;
            this.title = title;
        }
    }

    public static class BatchState {
        public final String batchId;
        public final String targetLang;
        public final String voiceId;
        public final int totalContents;
        public final int skippedContents;
        public final int queuedContents;
        public final java.util.concurrent.atomic.AtomicInteger processedContents = new java.util.concurrent.atomic.AtomicInteger(0);
        public final java.util.concurrent.atomic.AtomicInteger failedContents = new java.util.concurrent.atomic.AtomicInteger(0);
        public volatile String status = "PROCESSING";
        public final java.time.Instant createdAt = java.time.Instant.now();
        public volatile java.time.Instant updatedAt = java.time.Instant.now();
        public volatile boolean cancelled = false;
        public final List<String> createdJobIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        public BatchState(String batchId, String targetLang, String voiceId, int totalContents, int skippedContents, int queuedContents) {
            this.batchId = batchId;
            this.targetLang = targetLang;
            this.voiceId = voiceId;
            this.totalContents = totalContents;
            this.skippedContents = skippedContents;
            this.queuedContents = queuedContents;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("batchId", batchId);
            map.put("targetLang", targetLang);
            map.put("voiceId", voiceId);
            map.put("totalContents", totalContents);
            map.put("skippedContents", skippedContents);
            map.put("queuedContents", queuedContents);
            map.put("processedContents", processedContents.get());
            map.put("failedContents", failedContents.get());
            map.put("status", status);
            map.put("createdAt", createdAt.toString());
            map.put("updatedAt", updatedAt.toString());
            map.put("createdJobIds", createdJobIds);
            return map;
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, BatchState> batchStates = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ExecutorService batchExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    @SuppressWarnings("unchecked")
    public Map<String, Object> startBatchTranslate(String targetLang, String voiceId, String userId, String correlationId) {
        if (targetLang == null || targetLang.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TARGET_LANGUAGE", "Thiếu ngôn ngữ đích");
        }
        String lang = targetLang.toLowerCase().trim();

        Map<String, Object> supported = voices();
        Object voiceRows = supported.get("supportedVoices");
        if (!(voiceRows instanceof List<?> voiceList)) throw unavailable("TTS");

        Map<String, Object> langRow = voiceList.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .filter(row -> lang.equalsIgnoreCase(String.valueOf(row.get("lang"))))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "LANGUAGE_NOT_ENABLED", "Ngôn ngữ '" + lang + "' chưa được kích hoạt hoặc không khả dụng trên hệ thống"));

        List<String> voiceOptions = (List<String>) langRow.get("voices");
        String finalVoiceId = (voiceId != null && !voiceId.isBlank()) ? voiceId : (voiceOptions == null || voiceOptions.isEmpty() ? "" : voiceOptions.get(0));

        List<Map<String, Object>> contentsList = new ArrayList<>();
        try {
            Map<?, ?> lookupRes = content.get().uri("/internal/contents/lookup")
                    .header("X-Service-Token", serviceToken)
                    .retrieve().body(Map.class);
            if (lookupRes != null && lookupRes.get("data") instanceof Map<?, ?> data && data.get("items") instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) contentsList.add((Map<String, Object>) m);
                }
            }
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CONTENT_SERVICE_UNAVAILABLE", "Không thể lấy danh sách nội dung từ Content Service");
        }

        List<BatchJobItem> toProcess = new ArrayList<>();
        int skipped = 0;

        for (Map<String, Object> c : contentsList) {
            String contentId = String.valueOf(c.get("contentId"));
            Map<String, Object> currentInfo;
            try {
                currentInfo = readCurrentContent(contentId, correlationId);
            } catch (Exception ex) {
                skipped++;
                continue;
            }
            int currentVersion = ((Number) currentInfo.get("version")).intValue();
            String sourceLang = String.valueOf(currentInfo.get("sourceLang"));

            if (lang.equalsIgnoreCase(sourceLang)) {
                skipped++;
                continue;
            }

            List<Map<String, Object>> pubs = store.publishedLanguages(contentId);
            boolean alreadyPublished = pubs.stream().anyMatch(p -> lang.equalsIgnoreCase(String.valueOf(p.get("lang")))
                    && ((Number) p.get("version")).intValue() >= currentVersion);
            if (alreadyPublished) {
                skipped++;
                continue;
            }

            if (store.hasActiveTarget(contentId, currentVersion, lang)) {
                skipped++;
                continue;
            }

            toProcess.add(new BatchJobItem(contentId, currentVersion, String.valueOf(c.get("poiName")), String.valueOf(c.get("title"))));
        }

        String batchId = UUID.randomUUID().toString();
        BatchState state = new BatchState(batchId, lang, finalVoiceId, contentsList.size(), skipped, toProcess.size());
        if (toProcess.isEmpty()) {
            state.status = "COMPLETED";
        }
        batchStates.put(batchId, state);

        if (!toProcess.isEmpty()) {
            batchExecutor.submit(() -> {
                for (BatchJobItem item : toProcess) {
                    if (state.cancelled) {
                        state.status = "CANCELLED";
                        state.updatedAt = java.time.Instant.now();
                        break;
                    }
                    try {
                        String idempKey = "batch-" + batchId + "-" + item.contentId + "-v" + item.version;
                        Map<String, Object> job = createJob(item.contentId, null, item.version,
                                List.of(new TargetRequest(lang, finalVoiceId)), idempKey, userId, correlationId);
                        if (job != null && job.get("jobId") != null) {
                            state.createdJobIds.add(String.valueOf(job.get("jobId")));
                        }
                        state.processedContents.incrementAndGet();
                    } catch (Exception ex) {
                        state.failedContents.incrementAndGet();
                        System.err.println("[BATCH] Failed to create job for content " + item.contentId + ": " + ex.getMessage());
                    }
                    state.updatedAt = java.time.Instant.now();
                    try { Thread.sleep(80); } catch (InterruptedException ignored) {}
                }
                if (!state.cancelled) {
                    state.status = "COMPLETED";
                    state.updatedAt = java.time.Instant.now();
                }
            });
        }

        return state.toMap();
    }

    public Map<String, Object> getBatchTranslate(String batchId) {
        BatchState state = batchStates.get(batchId);
        if (state == null) throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Không tìm thấy tiến trình dịch hàng loạt");
        return state.toMap();
    }

    public Map<String, Object> cancelBatchTranslate(String batchId) {
        BatchState state = batchStates.get(batchId);
        if (state == null) throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Không tìm thấy tiến trình dịch hàng loạt");
        state.cancelled = true;
        state.status = "CANCELLED";
        state.updatedAt = java.time.Instant.now();
        return state.toMap();
    }
}
