package vn.edu.cnpm.tts.service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
@Repository
public class TtsStore {
    private final JdbcTemplate jdbc;private final Clock clock;
    public TtsStore(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public Map<String,Object> findAudio(String translationId,String voiceId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT audio_id AS \"audioId\",translation_id AS \"translationId\",voice_id AS \"voiceId\",object_key AS \"objectKey\",duration_seconds AS \"durationSeconds\",size_bytes AS \"sizeBytes\",created_at AS \"createdAt\" FROM audios WHERE translation_id=? AND voice_id=?",translationId,voiceId);
        return rows.isEmpty()?null:rows.get(0);
    }
    public Map<String,Object> getAudioById(String id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT audio_id AS \"audioId\",translation_id AS \"translationId\",voice_id AS \"voiceId\",object_key AS \"objectKey\",duration_seconds AS \"durationSeconds\",size_bytes AS \"sizeBytes\",created_at AS \"createdAt\" FROM audios WHERE audio_id=?",id);
        if(rows.isEmpty())throw new vn.edu.cnpm.tts.api.ApiException(org.springframework.http.HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy audio");
        return rows.get(0);
    }
    public Map<String,Object> getAudio(String id){return getAudioById(id);}
    @Transactional
    public Map<String,Object> save(String translationId,String voiceId,String objectKey,int duration,long size){
        Map<String,Object> cached=findAudio(translationId,voiceId);
        String now=clock.instant().toString();
        if(cached!=null) {
            String audioId = String.valueOf(cached.get("audioId"));
            jdbc.update("UPDATE audios SET object_key=?, duration_seconds=?, size_bytes=?, created_at=? WHERE audio_id=?",
                    objectKey, duration, size, now, audioId);
            return Map.of("audioId",audioId,"translationId",translationId,"voiceId",voiceId,"objectKey",objectKey,
                    "durationSeconds",duration,"sizeBytes",size,"createdAt",now);
        }
        String id=UUID.randomUUID().toString();
        try{jdbc.update("INSERT INTO audios(audio_id,translation_id,voice_id,object_key,duration_seconds,size_bytes,created_at) VALUES(?,?,?,?,?,?,?)",id,translationId,voiceId,objectKey,duration,size,now);}
        catch(DuplicateKeyException ex){
            jdbc.update("UPDATE audios SET object_key=?, duration_seconds=?, size_bytes=?, created_at=? WHERE translation_id=? AND voice_id=?",
                    objectKey, duration, size, now, translationId, voiceId);
            return findAudio(translationId,voiceId);
        }
        return Map.of("audioId",id,"translationId",translationId,"voiceId",voiceId,"objectKey",objectKey,
                "durationSeconds",duration,"sizeBytes",size,"createdAt",now);
    }
    public boolean isCancelled(String jobId){
        List<String> rows=jdbc.query("SELECT expires_at FROM cancelled_jobs WHERE job_id=?",(rs,n)->rs.getString(1),jobId);
        if(rows.isEmpty())return false;if(java.time.Instant.parse(rows.get(0)).isAfter(clock.instant()))return true;
        jdbc.update("DELETE FROM cancelled_jobs WHERE job_id=?",jobId);return false;
    }
    @Transactional public void cancel(String jobId){
        String expiry=clock.instant().plus(Duration.ofHours(48)).toString();
        if(jdbc.update("UPDATE cancelled_jobs SET expires_at=? WHERE job_id=?",expiry,jobId)==0){
            try{jdbc.update("INSERT INTO cancelled_jobs(job_id,expires_at) VALUES(?,?)",jobId,expiry);}
            catch(DuplicateKeyException ex){jdbc.update("UPDATE cancelled_jobs SET expires_at=? WHERE job_id=?",expiry,jobId);}
        }
    }
    public int nextInternalAttempt(String id){
        String now=clock.instant().toString();
        if(jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,id)>0)return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,id);
        try{jdbc.update("INSERT INTO event_attempts(event_id,attempts,updated_at) VALUES(?,1,?)",id,now);return 1;}
        catch(DuplicateKeyException ex){jdbc.update("UPDATE event_attempts SET attempts=attempts+1,updated_at=? WHERE event_id=?",now,id);return jdbc.queryForObject("SELECT attempts FROM event_attempts WHERE event_id=?",Integer.class,id);}
    }
    public void clearAttempt(String id){if(id!=null)jdbc.update("DELETE FROM event_attempts WHERE event_id=?",id);}
}
