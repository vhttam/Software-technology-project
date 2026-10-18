package vn.edu.cnpm.narration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.narration.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class NarrationStore {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final Clock clock;
    public NarrationStore(JdbcTemplate jdbc,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.json=json;this.clock=clock;}

    public Map<String,Object> findIdempotent(String userId,String key,String hash) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT request_hash,job_id FROM idempotency_records WHERE user_id=? AND operation='create-job' AND idempotency_key=?",userId,key);
        if(rows.isEmpty())return null;
        if(!hash.equals(rows.get(0).get("request_hash")))
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key đã được dùng cho request khác");
        return getJob(String.valueOf(rows.get(0).get("job_id")));
    }

    @Transactional
    public Map<String,Object> createJob(String userId,String key,String requestHash,String contentId,int version,
                                       List<JobTargetInput> inputs,String retryOfJobId,String correlationId) {
        String lockKey=contentId+"/"+version;
        try{jdbc.update("INSERT INTO job_creation_locks(lock_key) VALUES(?)",lockKey);}
        catch(DuplicateKeyException ignored){}
        jdbc.update("UPDATE job_creation_locks SET lock_key=lock_key WHERE lock_key=?",lockKey);
        Map<String,Object> prior=findIdempotent(userId,key,requestHash);
        if(prior!=null)return prior;
        Map<String,Object> retry=null;
        if(retryOfJobId!=null&&!retryOfJobId.isBlank()) {
            retry=findJobRow(retryOfJobId);
            if(retry==null||!contentId.equals(retry.get("content_id")))
                throw new ApiException(HttpStatus.CONFLICT,"INVALID_RETRY_REFERENCE","Job tham chiếu không tồn tại hoặc thuộc nội dung khác");
            if(((Number)retry.get("version")).intValue()!=version)
                throw new ApiException(HttpStatus.CONFLICT,"CONTENT_VERSION_CHANGED","Phiên bản nội dung đã thay đổi");
            for(JobTargetInput input:inputs) {
                if(findTargetByLang(retryOfJobId,input.lang())==null)
                    throw new ApiException(HttpStatus.CONFLICT,"INVALID_RETRY_REFERENCE","Job tham chiếu không có target tương ứng");
            }
        }
        List<String> conflicts=new ArrayList<>();
        for(JobTargetInput input:inputs) {
            List<String> active=jdbc.query("""
                    SELECT j.job_id FROM jobs j JOIN job_targets t ON t.job_id=j.job_id
                     WHERE j.content_id=? AND j.version=? AND j.status IN ('PENDING','PROCESSING')
                       AND t.lang=? AND t.status IN ('PENDING','TRANSLATING','SYNTHESIZING')
                    """,(rs,n)->rs.getString(1),contentId,version,input.lang());
            for(String jobId:active) {
                boolean retryCovers=retryOfJobId!=null&&retryOfJobId.equals(jobId)&&findTargetByLang(jobId,input.lang())!=null;
                if(!retryCovers&&!conflicts.contains(jobId))conflicts.add(jobId);
            }
        }
        if(!conflicts.isEmpty())throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_JOB_EXISTS","Đã có job đang xử lý ngôn ngữ này",Map.of("jobIds",conflicts));

        String jobId=UUID.randomUUID().toString(),now=clock.instant().toString();
        jdbc.update("INSERT INTO jobs(job_id,content_id,version,status,created_by,correlation_id,created_at,updated_at) VALUES(?,?,?,'PENDING',?,?,?,?)",
                jobId,contentId,version,userId,correlationId,now,now);
        List<Map<String,Object>> targets=new ArrayList<>();
        for(JobTargetInput input:inputs) {
            String targetId=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO job_targets(target_id,job_id,lang,voice_id,status,error_code,translation_id,audio_id,updated_at) VALUES(?,?,?,?,'PENDING',NULL,NULL,NULL,?)",
                    targetId,jobId,input.lang(),input.voiceId(),now);
            Map<String,Object> target=new LinkedHashMap<>();
            target.put("targetId",targetId);target.put("lang",input.lang());target.put("voiceId",input.voiceId());target.put("status","PENDING");
            targets.add(target);
            Map<String,Object> event=Map.of("jobId",jobId,"targetId",targetId,"contentId",contentId,
                    "version",version,"lang",input.lang(),"voiceId",input.voiceId());
            insertOutbox("narration.events","narration.requested",event,correlationId,now);
        }
        try{
            jdbc.update("INSERT INTO idempotency_records(record_id,user_id,operation,idempotency_key,request_hash,job_id,created_at,expires_at) VALUES(?,?,'create-job',?,?,?,?,?)",
                    UUID.randomUUID().toString(),userId,key,requestHash,jobId,now,clock.instant().plus(Duration.ofDays(2)).toString());
        }catch(DuplicateKeyException ex){
            Map<String,Object> duplicate=findIdempotent(userId,key,requestHash);
            if(duplicate!=null)return duplicate;
            throw new ApiException(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED","Idempotency-Key đã được dùng cho request khác");
        }
        return getJob(jobId);
    }

    public Map<String,Object> getJob(String jobId) {
        Map<String,Object> job=findJobRow(jobId);
        if(job==null)throw new ApiException(HttpStatus.NOT_FOUND,"JOB_NOT_FOUND","Không tìm thấy job");
        List<Map<String,Object>> targets;
        try {
            targets=jdbc.query("""
                    SELECT target_id,lang,voice_id,status,error_code,error_message,updated_at FROM job_targets WHERE job_id=? ORDER BY lang
                    """,(rs,n)->{
                Map<String,Object> t=new LinkedHashMap<>();t.put("targetId",rs.getString("target_id"));t.put("lang",rs.getString("lang"));
                t.put("voiceId",rs.getString("voice_id"));t.put("status",rs.getString("status"));t.put("errorCode",rs.getString("error_code"));
                t.put("errorMessage",rs.getString("error_message"));
                t.put("updatedAt",rs.getString("updated_at"));return t;
            },jobId);
        } catch (Exception ex) {
            targets=jdbc.query("""
                    SELECT target_id,lang,voice_id,status,error_code,updated_at FROM job_targets WHERE job_id=? ORDER BY lang
                    """,(rs,n)->{
                Map<String,Object> t=new LinkedHashMap<>();t.put("targetId",rs.getString("target_id"));t.put("lang",rs.getString("lang"));
                t.put("voiceId",rs.getString("voice_id"));t.put("status",rs.getString("status"));t.put("errorCode",rs.getString("error_code"));
                t.put("errorMessage",null);
                t.put("updatedAt",rs.getString("updated_at"));return t;
            },jobId);
        }
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("jobId",job.get("job_id"));result.put("contentId",job.get("content_id"));result.put("version",job.get("version"));
        result.put("status",job.get("status"));result.put("createdAt",job.get("created_at"));result.put("updatedAt",job.get("updated_at"));
        result.put("targets",targets);return result;
    }

    public Map<String,Object> listJobs(int page, int pageSize, String status, String lang, String dateRange, List<String> contentIds) {
        if (contentIds != null && contentIds.isEmpty()) {
            return Map.of("items", List.of(), "page", page, "pageSize", pageSize, "totalItems", 0, "totalPages", 0);
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            where.append(" AND j.status=?");
            args.add(status.trim().toUpperCase());
        }

        if (lang != null && !lang.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM job_targets t WHERE t.job_id=j.job_id AND t.lang=?)");
            args.add(lang.trim().toLowerCase());
        }

        if (dateRange != null && !dateRange.isBlank()) {
            if ("today".equalsIgnoreCase(dateRange.trim())) {
                Instant todayStart = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
                where.append(" AND j.created_at >= ?");
                args.add(todayStart.toString());
            } else if ("7days".equalsIgnoreCase(dateRange.trim()) || "week".equalsIgnoreCase(dateRange.trim())) {
                Instant sevenDaysAgo = clock.instant().minus(Duration.ofDays(7));
                where.append(" AND j.created_at >= ?");
                args.add(sevenDaysAgo.toString());
            }
        }

        if (contentIds != null) {
            String placeholders = String.join(",", Collections.nCopies(contentIds.size(), "?"));
            where.append(" AND j.content_id IN (").append(placeholders).append(")");
            args.addAll(contentIds);
        }

        String selectSql = "SELECT j.job_id, j.content_id, j.version, j.status, j.created_by, j.correlation_id, j.created_at, j.updated_at FROM jobs j"
                + where + " ORDER BY j.created_at DESC";

        List<Map<String,Object>> allJobs = jdbc.query(selectSql, args.toArray(), (rs, n) -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("jobId", rs.getString("job_id"));
            m.put("contentId", rs.getString("content_id"));
            m.put("version", rs.getInt("version"));
            m.put("status", rs.getString("status"));
            m.put("createdBy", rs.getString("created_by"));
            m.put("createdAt", rs.getString("created_at"));
            m.put("updatedAt", rs.getString("updated_at"));
            return m;
        });

        int totalItems = allJobs.size();
        int totalPages = totalItems == 0 ? 0 : (totalItems + pageSize - 1) / pageSize;
        int start = Math.min((page - 1) * pageSize, totalItems);
        int end = Math.min(start + pageSize, totalItems);
        List<Map<String,Object>> jobs = new ArrayList<>(allJobs.subList(start, end));

        if (!jobs.isEmpty()) {
            List<String> jobIds = jobs.stream().map(j -> (String) j.get("jobId")).toList();
            String inClause = String.join(",", Collections.nCopies(jobIds.size(), "?"));
            String targetsSql = "SELECT target_id, job_id, lang, voice_id, status, error_code, updated_at FROM job_targets WHERE job_id IN (" + inClause + ") ORDER BY lang";
            List<Map<String,Object>> allTargets = jdbc.query(targetsSql, jobIds.toArray(), (rs, n) -> {
                Map<String,Object> t = new LinkedHashMap<>();
                t.put("targetId", rs.getString("target_id"));
                t.put("jobId", rs.getString("job_id"));
                t.put("lang", rs.getString("lang"));
                t.put("voiceId", rs.getString("voice_id"));
                t.put("status", rs.getString("status"));
                t.put("errorCode", rs.getString("error_code"));
                t.put("updatedAt", rs.getString("updated_at"));
                return t;
            });
            Map<String, List<Map<String,Object>>> targetsByJob = new LinkedHashMap<>();
            for (Map<String,Object> t : allTargets) {
                String jId = (String) t.get("jobId");
                targetsByJob.computeIfAbsent(jId, k -> new ArrayList<>()).add(t);
            }
            for (Map<String,Object> j : jobs) {
                j.put("targets", targetsByJob.getOrDefault(j.get("jobId"), List.of()));
            }
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("items", jobs);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalItems", totalItems);
        result.put("totalPages", totalPages);
        return result;
    }

    @Transactional
    public Map<String,Object> cancelJob(String jobId) {
        lockJob(jobId);
        Map<String,Object> job=findJobRow(jobId);
        if(job==null)throw new ApiException(HttpStatus.NOT_FOUND,"JOB_NOT_FOUND","Không tìm thấy job");
        String status=String.valueOf(job.get("status"));
        if(!status.equals("PENDING")&&!status.equals("PROCESSING"))
            throw new ApiException(HttpStatus.CONFLICT,"JOB_ALREADY_FINISHED","Job đã kết thúc");
        String now=clock.instant().toString();
        jdbc.update("UPDATE jobs SET status='CANCELLED',updated_at=? WHERE job_id=?",now,jobId);
        jdbc.update("UPDATE job_targets SET status='CANCELLED',updated_at=? WHERE job_id=? AND status<>'FAILED'",now,jobId);
        insertOutbox("narration.cancel","",Map.of("jobId",jobId),String.valueOf(job.get("correlation_id")),now);
        updateIdempotencyExpiry(jobId,now);
        return getJob(jobId);
    }

    @Transactional
    public Map<String,Object> applyTranslationCompleted(String jobId,String targetId,String translationId) {
        lockJob(jobId);Map<String,Object> job=findJobRow(jobId);
        if(job==null)return null;
        Map<String,Object> target=findTarget(targetId,jobId);if(target==null)return null;
        String status=String.valueOf(target.get("status"));
        if(isFinal(status)||"CANCELLED".equals(job.get("status")))return null;
        String now=clock.instant().toString();
        jdbc.update("UPDATE job_targets SET status=CASE WHEN status IN ('PENDING','TRANSLATING') THEN 'SYNTHESIZING' ELSE status END,translation_id=COALESCE(translation_id,?),updated_at=? WHERE target_id=?",
                translationId,now,targetId);
        return refreshJobAndMaybeFinish(jobId,now);
    }

    @Transactional
    public Map<String,Object> applyTtsCompleted(String jobId,String targetId,String translationId,String audioId) {
        lockJob(jobId);Map<String,Object> job=findJobRow(jobId);
        if(job==null)return null;Map<String,Object> target=findTarget(targetId,jobId);if(target==null)return null;
        String status=String.valueOf(target.get("status"));
        if(isFinal(status)||"CANCELLED".equals(job.get("status")))return null;
        String now=clock.instant().toString();
        jdbc.update("UPDATE job_targets SET status='PUBLISHED',translation_id=COALESCE(translation_id,?),audio_id=COALESCE(audio_id,?),updated_at=? WHERE target_id=?",
                translationId,audioId,now,targetId);
        return refreshJobAndMaybeFinish(jobId,now);
    }

    @Transactional
    public Map<String,Object> applyFailure(String jobId,String targetId,String errorCode) {
        lockJob(jobId);Map<String,Object> job=findJobRow(jobId);
        if(job==null)return null;Map<String,Object> target=findTarget(targetId,jobId);if(target==null)return null;
        String status=String.valueOf(target.get("status"));
        if(isFinal(status)||"CANCELLED".equals(job.get("status")))return null;
        String now=clock.instant().toString();
        jdbc.update("UPDATE job_targets SET status='FAILED',error_code=?,updated_at=? WHERE target_id=?",errorCode,now,targetId);
        return refreshJobAndMaybeFinish(jobId,now);
    }

    @Transactional
    public Map<String,Object> markRequested(String targetId) {
        Map<String,Object> target=findTargetById(targetId);if(target==null)return null;
        String jobId=String.valueOf(target.get("job_id"));lockJob(jobId);
        Map<String,Object> job=findJobRow(jobId);if(job==null||"CANCELLED".equals(job.get("status")))return null;
        Map<String,Object> current=findTargetById(targetId);String state=String.valueOf(current.get("status"));
        if(isFinal(state))return null;
        String now=clock.instant().toString();
        jdbc.update("UPDATE jobs SET status='PROCESSING',updated_at=? WHERE job_id=? AND status='PENDING'",now,jobId);
        jdbc.update("UPDATE job_targets SET status='TRANSLATING',updated_at=? WHERE target_id=? AND status='PENDING'",now,targetId);
        return getJob(jobId);
    }

    @Transactional
    public Map<String,Object> timeoutJob(String jobId,Duration translationTimeout,Duration ttsTimeout) {
        lockJob(jobId);Map<String,Object> job=findJobRow(jobId);
        if(job==null||!("PENDING".equals(job.get("status"))||"PROCESSING".equals(job.get("status"))))return null;
        String now=clock.instant().toString();Instant instant=clock.instant();boolean changed=false;
        List<Map<String,Object>> targets=jdbc.query("SELECT target_id,status,updated_at FROM job_targets WHERE job_id=?",
                (rs,n)->Map.of("targetId",rs.getString("target_id"),"status",rs.getString("status"),"updatedAt",rs.getString("updated_at")),jobId);
        for(Map<String,Object> t:targets) {
            String status=String.valueOf(t.get("status"));if(isFinal(status))continue;
            Instant updated=Instant.parse(String.valueOf(t.get("updatedAt")));
            Duration max= "SYNTHESIZING".equals(status)?ttsTimeout:translationTimeout;
            if(Duration.between(updated,instant).compareTo(max)>=0) {
                try {
                    jdbc.update("UPDATE job_targets SET status='FAILED',error_code='TIMEOUT',error_message='Quá thời gian xử lý',updated_at=? WHERE target_id=? AND status IN ('PENDING','TRANSLATING','SYNTHESIZING')",now,t.get("targetId"));
                } catch (Exception ex) {
                    jdbc.update("UPDATE job_targets SET status='FAILED',error_code='TIMEOUT',updated_at=? WHERE target_id=? AND status IN ('PENDING','TRANSLATING','SYNTHESIZING')",now,t.get("targetId"));
                }
                changed=true;
            }
        }
        return changed?refreshJobAndMaybeFinish(jobId,now):null;
    }

    public List<Map<String,Object>> jobsForTimeout() {
        return jdbc.query("SELECT job_id FROM jobs WHERE status IN ('PENDING','PROCESSING')",(rs,n)->Map.of("jobId",rs.getString(1)));
    }

    public List<Map<String,Object>> publishedLanguages(String contentId) {
        List<Map<String,Object>> all=jdbc.query("""
                SELECT t.lang,j.version,t.updated_at FROM job_targets t JOIN jobs j ON j.job_id=t.job_id
                 WHERE j.content_id=? AND t.status='PUBLISHED'
                """,(rs,n)->Map.of("lang",rs.getString("lang"),"version",rs.getInt("version"),"updatedAt",rs.getString("updated_at")),contentId);
        all.sort(Comparator.<Map<String,Object>,Integer>comparing(m->((Number)m.get("version")).intValue()).reversed()
                .thenComparing(m->String.valueOf(m.get("updatedAt")),Comparator.reverseOrder()));
        Map<String,Map<String,Object>> byLang=new LinkedHashMap<>();
        for(Map<String,Object> item:all)byLang.putIfAbsent(String.valueOf(item.get("lang")),item);
        return byLang.values().stream().map(m->Map.<String,Object>of("lang",m.get("lang"),"version",m.get("version"))).toList();
    }

    public Map<String,Object> publishedTarget(String contentId,String lang) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT t.lang,j.version,t.translation_id,t.audio_id,t.updated_at FROM job_targets t JOIN jobs j ON j.job_id=t.job_id
                 WHERE j.content_id=? AND t.lang=? AND t.status='PUBLISHED'
                """,(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("lang",rs.getString("lang"));m.put("version",rs.getInt("version"));
            m.put("translationId",rs.getString("translation_id"));m.put("audioId",rs.getString("audio_id"));m.put("updatedAt",rs.getString("updated_at"));return m;
        },contentId,lang);
        if(rows.isEmpty())return null;
        rows.sort(Comparator.<Map<String,Object>,Integer>comparing(m->((Number)m.get("version")).intValue()).reversed()
                .thenComparing(m->String.valueOf(m.get("updatedAt")),Comparator.reverseOrder()));
        return rows.get(0);
    }

    public boolean hasActiveTarget(String contentId, int version, String lang) {
        List<String> active = jdbc.query("""
                SELECT t.target_id FROM jobs j JOIN job_targets t ON t.job_id=j.job_id
                 WHERE j.content_id=? AND j.version=? AND j.status IN ('PENDING','PROCESSING')
                   AND t.lang=? AND t.status IN ('PENDING','TRANSLATING','SYNTHESIZING')
                """, (rs, n) -> rs.getString(1), contentId, version, lang);
        return !active.isEmpty();
    }

    public String correlationId(String jobId){Map<String,Object> job=findJobRow(jobId);return job==null?null:(String)job.get("correlation_id");}

    public int nextInternalAttempt(String id) {
        String now=clock.instant().toString();
        if(jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,id)>0)
            return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,id);
        try{jdbc.update("INSERT INTO event_attempts(event_id,attempts,updated_at) VALUES(?,1,?)",id,now);return 1;}
        catch(DuplicateKeyException ex){jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,id);return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,id);}
    }
    public void clearAttempt(String id){if(id!=null)jdbc.update("DELETE FROM event_attempts WHERE event_id=?",id);}

    private Map<String,Object> refreshJobAndMaybeFinish(String jobId,String now) {
        List<String> states=jdbc.query("SELECT status FROM job_targets WHERE job_id=?",(rs,n)->rs.getString(1),jobId);
        if(!states.isEmpty()&&states.stream().allMatch(this::isFinal)) {
            String aggregate;
            if(states.stream().allMatch("PUBLISHED"::equals)) aggregate="COMPLETED";
            else if(states.contains("PUBLISHED")) aggregate="PARTIALLY_COMPLETED";
            else if(states.stream().allMatch("FAILED"::equals)) aggregate="FAILED";
            else if(states.stream().allMatch("CANCELLED"::equals)) aggregate="CANCELLED";
            else aggregate="FAILED";
            jdbc.update("UPDATE jobs SET status=?,updated_at=? WHERE job_id=?",aggregate,now,jobId);
            updateIdempotencyExpiry(jobId,now);
        } else jdbc.update("UPDATE jobs SET status='PROCESSING',updated_at=? WHERE job_id=? AND status<>'CANCELLED'",now,jobId);
        return getJob(jobId);
    }
    private void updateIdempotencyExpiry(String jobId,String finishedAt){
        jdbc.update("UPDATE idempotency_records SET expires_at=? WHERE job_id=?",Instant.parse(finishedAt).plus(Duration.ofHours(24)).toString(),jobId);
    }
    private void insertOutbox(String exchange,String route,Object payload,String correlationId,String now) {
        try{jdbc.update("INSERT INTO outbox_events(event_id,exchange_name,routing_key,payload,correlation_id,occurred_at,published) VALUES(?,?,?,?,?,?,0)",
                UUID.randomUUID().toString(),exchange,route,json.writeValueAsString(payload),correlationId,now);}
        catch(Exception ex){throw new IllegalStateException("Không thể ghi outbox",ex);}
    }
    private void lockJob(String id){jdbc.update("UPDATE jobs SET job_id=job_id WHERE job_id=?",id);}
    private Map<String,Object> findJobRow(String id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT job_id,content_id,version,status,created_by,correlation_id,created_at,updated_at FROM jobs WHERE job_id=?",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Map<String,Object> findTarget(String targetId,String jobId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT target_id,job_id,lang,voice_id,status,error_code,translation_id,audio_id,updated_at FROM job_targets WHERE target_id=? AND job_id=?",targetId,jobId);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Map<String,Object> findTargetById(String id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT target_id,job_id,lang,voice_id,status,error_code,translation_id,audio_id,updated_at FROM job_targets WHERE target_id=?",id);
        return rows.isEmpty()?null:rows.get(0);
    }
    private Map<String,Object> findTargetByLang(String jobId,String lang){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT target_id,job_id,lang,voice_id,status FROM job_targets WHERE job_id=? AND lang=?",jobId,lang);
        return rows.isEmpty()?null:rows.get(0);
    }
    private boolean isFinal(String status){return "PUBLISHED".equals(status)||"FAILED".equals(status)||"CANCELLED".equals(status);}

    public record JobTargetInput(String lang,String voiceId){}
}
