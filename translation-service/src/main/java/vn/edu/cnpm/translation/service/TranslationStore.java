package vn.edu.cnpm.translation.service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.translation.api.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Repository
public class TranslationStore {
    private final JdbcTemplate jdbc; private final Clock clock;
    public TranslationStore(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc;this.clock=clock; }
    public Map<String,Object> findCached(String contentId,int version,String lang) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT translation_id AS \"translationId\",content_id AS \"contentId\",version,lang,text_content AS \"textContent\",created_at AS \"createdAt\" FROM translations WHERE content_id=? AND version=? AND lang=?",
                contentId,version,lang);
        return rows.isEmpty()?null:rows.get(0);
    }
    @Transactional
    public Map<String,Object> save(String contentId,int version,String lang,String text) {
        Map<String,Object> cached=findCached(contentId,version,lang);
        if(cached!=null)return cached;
        String id=UUID.randomUUID().toString(),now=clock.instant().toString();
        try { jdbc.update("INSERT INTO translations(translation_id,content_id,version,lang,text_content,created_at) VALUES(?,?,?,?,?,?)",
                id,contentId,version,lang,text,now); }
        catch(DuplicateKeyException ex) { return findCached(contentId,version,lang); }
        return Map.of("translationId",id,"contentId",contentId,"version",version,"lang",lang,"textContent",text,"createdAt",now);
    }
    public Map<String,Object> get(String id) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT translation_id AS \"translationId\",content_id AS \"contentId\",version,lang,text_content AS \"textContent\",created_at AS \"createdAt\" FROM translations WHERE translation_id=?",id);
        if(rows.isEmpty())throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy bản dịch");
        return rows.get(0);
    }
    public boolean isCancelled(String jobId) {
        List<String> rows=jdbc.query("SELECT expires_at FROM cancelled_jobs WHERE job_id=?",(rs,n)->rs.getString(1),jobId);
        if(rows.isEmpty())return false;
        if(java.time.Instant.parse(rows.get(0)).isAfter(clock.instant()))return true;
        jdbc.update("DELETE FROM cancelled_jobs WHERE job_id=?",jobId);return false;
    }
    @Transactional
    public void cancel(String jobId) {
        String expires=clock.instant().plus(Duration.ofHours(48)).toString();
        if(jdbc.update("UPDATE cancelled_jobs SET expires_at=? WHERE job_id=?",expires,jobId)==0) {
            try { jdbc.update("INSERT INTO cancelled_jobs(job_id,expires_at) VALUES(?,?)",jobId,expires); }
            catch(DuplicateKeyException ignored) { jdbc.update("UPDATE cancelled_jobs SET expires_at=? WHERE job_id=?",expires,jobId); }
        }
    }
    public int nextInternalAttempt(String eventId) {
        String now=clock.instant().toString();
        if(jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,eventId)>0)
            return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,eventId);
        try { jdbc.update("INSERT INTO event_attempts(event_id,attempts,updated_at) VALUES(?,1,?)",eventId,now); return 1; }
        catch(DuplicateKeyException ex) { jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,eventId); return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,eventId); }
    }
    public void clearAttempt(String eventId) { if(eventId!=null)jdbc.update("DELETE FROM event_attempts WHERE event_id=?",eventId); }
}
