package vn.edu.cnpm.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.content.api.ApiException;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import vn.edu.cnpm.content.config.LanguageRegistry;

@Service
public class ContentService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ContentService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.json = json; this.clock = clock;
    }

    public Map<String,Object> sourceLanguages() { return Map.of("languages", LanguageRegistry.getLanguageCodes()); }

    @Transactional
    public Map<String,Object> createPoi(String name, String description) {
        validatePoi(name, description);
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        String qr = null;
        for (int attempt=0; attempt<3; attempt++) {
            String candidate = newQrCode();
            if (!jdbc.queryForList("SELECT poi_id FROM points_of_interest WHERE qr_code=?", candidate).isEmpty()) continue;
            try {
                jdbc.update("INSERT INTO points_of_interest(poi_id,name,description,qr_code,created_at,updated_at,is_deleted) VALUES(?,?,?,?,?,?,0)",
                        id,name,description,candidate,now.toString(),now.toString());
                qr=candidate;
                break;
            } catch (DataIntegrityViolationException ex) {
                // Sinh lại nếu một request đồng thời vừa dùng cùng mã QR.
            }
        }
        if (qr == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"QR_CODE_GENERATION_FAILED","Không thể tạo mã QR, vui lòng thử lại");
        return poi(id,name,description,qr,now.toString(),now.toString());
    }

    public Map<String,Object> getPoi(String id) { return findPoi(id).orElseThrow(this::poiMissing); }

    @Transactional
    public Map<String,Object> updatePoi(String id,String name,String description) {
        validatePoi(name,description);
        lockPoi(id);
        Map<String,Object> current=findPoi(id).orElseThrow(this::poiMissing);
        String now=clock.instant().toString();
        jdbc.update("UPDATE points_of_interest SET name=?,description=?,updated_at=? WHERE poi_id=?",name,description,now,id);
        return poi(id,name,description,(String)current.get("qrCode"),(String)current.get("createdAt"),now);
    }

    @Transactional
    public void deletePoi(String id) {
        lockPoi(id);
        if (findPoi(id).isEmpty()) throw poiMissing();
        Integer activeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM contents WHERE poi_id=? AND is_deleted=0", Integer.class, id);
        if (activeCount != null && activeCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "POI_HAS_CONTENT",
                    "Điểm còn " + activeCount + " nội dung đang hoạt động, hãy xóa nội dung trước");
        }
        String now = clock.instant().toString();
        jdbc.update("UPDATE points_of_interest SET is_deleted=1, deleted_at=?, updated_at=? WHERE poi_id=?", now, now, id);
    }

    public Map<String,Object> listPois(int page,int pageSize,String query) {
        validatePage(page,pageSize);
        List<Map<String,Object>> all = jdbc.query("SELECT * FROM points_of_interest WHERE is_deleted=0 ORDER BY name", this::poiRow);
        if (query != null && !query.isBlank()) {
            String q = LanguageRegistry.unaccent(query);
            all = all.stream().filter(p -> LanguageRegistry.unaccent((String) p.get("name")).contains(q)).toList();
        }
        return page(all,page,pageSize);
    }

    @Transactional
    public Map<String,Object> createContent(String poiId,String title,String sourceLang,String textContent,String createdBy,String correlationId) {
        validateContent(title,sourceLang,textContent);
        lockPoi(poiId);
        if (findPoi(poiId).isEmpty()) throw poiMissing();
        if (!jdbc.queryForList("SELECT content_id FROM contents WHERE poi_id=? AND is_deleted=0",poiId).isEmpty())
            throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_CONTENT_EXISTS","Điểm tham quan đã có nội dung đang hoạt động");
        String id=UUID.randomUUID().toString(), now=clock.instant().toString();
        try {
            jdbc.update("INSERT INTO contents(content_id,poi_id,current_version,is_deleted,created_by,created_at,updated_at) VALUES(?,?,1,0,?,?,?)",
                    id,poiId,createdBy,now,now);
            jdbc.update("INSERT INTO content_versions(content_id,version,title,source_lang,text_content,created_at) VALUES(?,1,?,?,?,?)",
                    id,title,sourceLang,textContent,now);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT,"ACTIVE_CONTENT_EXISTS","Điểm tham quan đã có nội dung đang hoạt động");
        }
        insertOutbox("content.updated",Map.of("contentId",id,"version",1),correlationId,now);
        return content(id,poiId,1,title,sourceLang,textContent,false,createdBy,now,now);
    }

    public Map<String,Object> listContents(int page,int pageSize,String query,String poiId) {
        validatePage(page,pageSize);
        StringBuilder sql=new StringBuilder("""
                SELECT c.content_id,c.poi_id,c.current_version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,p.name AS poi_name
                  FROM contents c JOIN points_of_interest p ON p.poi_id=c.poi_id AND p.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version WHERE 1=1
                """);
        List<Object> args=new ArrayList<>();
        if (poiId!=null&&!poiId.isBlank()) { sql.append(" AND c.poi_id=?");args.add(poiId); }
        sql.append(" ORDER BY c.updated_at DESC");
        List<Map<String,Object>> rows=jdbc.query(sql.toString(),args.toArray(),(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("poiId",rs.getString("poi_id"));item.put("poiName",rs.getString("poi_name"));
            item.put("contentId",rs.getString("content_id"));item.put("title",rs.getString("title"));
            item.put("sourceLang",rs.getString("source_lang"));item.put("version",rs.getInt("current_version"));
            item.put("isDeleted",rs.getBoolean("is_deleted"));item.put("createdAt",rs.getString("created_at"));item.put("updatedAt",rs.getString("updated_at"));
            return item;
        });
        if (query != null && !query.isBlank()) {
            String q = LanguageRegistry.unaccent(query);
            rows = rows.stream().filter(item ->
                LanguageRegistry.unaccent((String) item.get("title")).contains(q) ||
                LanguageRegistry.unaccent((String) item.get("poiName")).contains(q)
            ).toList();
        }
        return page(rows,page,pageSize);
    }

    public Map<String,Object> activeContentByPoi(String poiId) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT c.content_id,c.poi_id,c.current_version AS version,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,v.text_content
                  FROM contents c JOIN points_of_interest p ON p.poi_id=c.poi_id AND p.is_deleted=0
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.poi_id=? AND c.is_deleted=0
                """,(rs,n)->content(rs),poiId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy nội dung đang hoạt động");
        return rows.get(0);
    }

    public Map<String,Object> getContent(String contentId) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT c.content_id,c.poi_id,c.current_version AS version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.title,v.source_lang,v.text_content
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                 WHERE c.content_id=? AND c.is_deleted=0
                """,(rs,n)->content(rs),contentId);
        if (rows.isEmpty()) throw contentMissing();
        return rows.get(0);
    }

    @Transactional
    public Map<String,Object> updateContent(String contentId,int baseVersion,String title,String sourceLang,String textContent,String correlationId) {
        validateContent(title,sourceLang,textContent);
        jdbc.update("UPDATE contents SET updated_at=updated_at WHERE content_id=? AND is_deleted=0",contentId);
        List<Integer> current=jdbc.query("SELECT current_version FROM contents WHERE content_id=? AND is_deleted=0",
                (rs,n)->rs.getInt(1),contentId);
        if(current.isEmpty()) throw contentMissing();
        int version=current.get(0);
        if(version!=baseVersion) throw new ApiException(HttpStatus.CONFLICT,"CONTENT_VERSION_CONFLICT","Nội dung đã được cập nhật bởi người khác",Map.of("currentVersion",version));
        int next=baseVersion+1;String now=clock.instant().toString();
        jdbc.update("INSERT INTO content_versions(content_id,version,title,source_lang,text_content,created_at) VALUES(?,?,?,?,?,?)",
                contentId,next,title,sourceLang,textContent,now);
        jdbc.update("UPDATE contents SET current_version=?,updated_at=? WHERE content_id=?",next,now,contentId);
        Map<String,Object> meta=jdbc.queryForMap("SELECT poi_id,created_by,created_at FROM contents WHERE content_id=?",contentId);
        insertOutbox("content.updated",Map.of("contentId",contentId,"version",next),correlationId,now);
        return content(contentId,String.valueOf(meta.get("poi_id")),next,title,sourceLang,textContent,false,
                String.valueOf(meta.get("created_by")),String.valueOf(meta.get("created_at")),now);
    }

    @Transactional
    public void deleteContent(String contentId,String correlationId) {
        jdbc.update("UPDATE contents SET updated_at=updated_at WHERE content_id=? AND is_deleted=0",contentId);
        if(jdbc.update("UPDATE contents SET is_deleted=1,updated_at=? WHERE content_id=? AND is_deleted=0",clock.instant().toString(),contentId)==0)
            throw contentMissing();
        insertOutbox("content.deleted",Map.of("contentId",contentId),correlationId,clock.instant().toString());
    }

    public Map<String,Object> exactVersion(String contentId,int version) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT c.content_id,c.poi_id,c.current_version,c.is_deleted,c.created_by,c.created_at,c.updated_at,
                       v.version AS version,v.title,v.source_lang,v.text_content
                  FROM contents c JOIN content_versions v ON v.content_id=c.content_id
                 WHERE c.content_id=? AND v.version=?
                """,(rs,n)->content(rs),contentId,version);
        if(rows.isEmpty()) throw contentMissing();
        return rows.get(0);
    }

    public Map<String,Object> currentForJob(String contentId) {
        Map<String,Object> result=getContent(contentId);
        result.remove("poiId");result.remove("title");result.remove("textContent");result.remove("createdBy");
        result.remove("createdAt");result.remove("updatedAt");result.remove("isDeleted");
        return result;
    }

    public Map<String,Object> listVisitorPois(int page,int pageSize,String query) {
        validatePage(page,pageSize);
        String q=query==null||query.isBlank()?null:"%"+query.toLowerCase()+"%";
        String sql="""
                SELECT DISTINCT p.poi_id,p.name,p.description FROM points_of_interest p
                JOIN contents c ON c.poi_id=p.poi_id AND c.is_deleted=0
                WHERE p.is_deleted=0
                """;
        if(q!=null) sql += " AND LOWER(p.name) LIKE ?";
        sql += " ORDER BY p.name";
        org.springframework.jdbc.core.RowMapper<Map<String,Object>> mapper=(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("poiId",rs.getString("poi_id"));item.put("name",rs.getString("name"));
            item.put("description",rs.getString("description"));return item;
        };
        List<Map<String,Object>> rows=q==null?jdbc.query(sql,mapper):jdbc.query(sql,mapper,q);
        return page(rows,page,pageSize);
    }

    public Map<String,Object> visitorPoiCurrent(String poiId) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT p.poi_id,p.name AS poi_name,v.title,c.content_id FROM points_of_interest p
                JOIN contents c ON c.poi_id=p.poi_id AND c.is_deleted=0
                JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version WHERE p.poi_id=? AND p.is_deleted=0
                """,(rs,n)->Map.of("poiId",rs.getString("poi_id"),"poiName",rs.getString("poi_name"),
                "contentId",rs.getString("content_id"),"contentTitle",rs.getString("title")),poiId);
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"POI_NOT_FOUND","Không tìm thấy điểm tham quan hoặc nội dung đang hoạt động");
        return rows.get(0);
    }

    public Map<String,Object> visitorPoiByQr(String qrCode) {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT p.poi_id,p.name AS poi_name,v.title,c.content_id FROM points_of_interest p
                JOIN contents c ON c.poi_id=p.poi_id AND c.is_deleted=0
                JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version WHERE p.qr_code=? AND p.is_deleted=0
                """,(rs,n)->Map.of("poiId",rs.getString("poi_id"),"poiName",rs.getString("poi_name"),
                "contentId",rs.getString("content_id"),"contentTitle",rs.getString("title")),qrCode);
        if(rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND,"POI_NOT_FOUND","Không tìm thấy điểm tham quan hoặc nội dung đang hoạt động");
        return rows.get(0);
    }

    public Map<String,Object> contentsLookup() {
        List<Map<String,Object>> rows=jdbc.query("""
                SELECT c.content_id,c.poi_id,p.name AS poi_name,v.title
                  FROM contents c
                  JOIN points_of_interest p ON p.poi_id=c.poi_id
                  JOIN content_versions v ON v.content_id=c.content_id AND v.version=c.current_version
                """,(rs,n)->{
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("contentId",rs.getString("content_id"));
            item.put("poiId",rs.getString("poi_id"));
            item.put("poiName",rs.getString("poi_name"));
            item.put("title",rs.getString("title"));
            return item;
        });
        return Map.of("items",rows);
    }

    private void lockPoi(String poiId) { jdbc.update("UPDATE points_of_interest SET updated_at=updated_at WHERE poi_id=? AND is_deleted=0",poiId); }
    private java.util.Optional<Map<String,Object>> findPoi(String id) {
        List<Map<String,Object>> result=jdbc.query("SELECT * FROM points_of_interest WHERE poi_id=? AND is_deleted=0",this::poiRow,id);
        return result.stream().findFirst();
    }
    private Map<String,Object> poiRow(ResultSet rs,int row) throws SQLException {
        return poi(rs.getString("poi_id"),rs.getString("name"),rs.getString("description"),rs.getString("qr_code"),
                rs.getString("created_at"),rs.getString("updated_at"));
    }
    private Map<String,Object> poi(String id,String name,String description,String qr,String created,String updated) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("poiId",id);result.put("name",name);result.put("description",description);result.put("qrCode",qr);
        result.put("createdAt",created);result.put("updatedAt",updated);return result;
    }
    private Map<String,Object> content(ResultSet rs) throws SQLException {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("contentId",rs.getString("content_id"));result.put("poiId",rs.getString("poi_id"));
        result.put("version",rs.getInt("version"));
        result.put("title",rs.getString("title"));result.put("sourceLang",rs.getString("source_lang"));
        result.put("textContent",rs.getString("text_content"));result.put("isDeleted",rs.getBoolean("is_deleted"));
        result.put("createdBy",rs.getString("created_by"));result.put("createdAt",rs.getString("created_at"));
        result.put("updatedAt",rs.getString("updated_at"));return result;
    }
    private Map<String,Object> content(String id,String poi,int version,String title,String lang,String text,boolean deleted,String by,String created,String updated) {
        Map<String,Object> result=new LinkedHashMap<>();result.put("contentId",id);result.put("poiId",poi);result.put("version",version);
        result.put("title",title);result.put("sourceLang",lang);result.put("textContent",text);result.put("isDeleted",deleted);
        result.put("createdBy",by);result.put("createdAt",created);result.put("updatedAt",updated);return result;
    }
    private void insertOutbox(String route,Map<String,Object> payload,String correlationId,String now) {
        try {
            jdbc.update("INSERT INTO outbox_events(event_id,routing_key,payload,correlation_id,occurred_at,published) VALUES(?,?,?,?,?,0)",
                    UUID.randomUUID().toString(),route,json.writeValueAsString(payload),correlationId,now);
        } catch(Exception ex) { throw new IllegalStateException("Không thể ghi sự kiện outbox",ex); }
    }
    private Map<String,Object> page(List<Map<String,Object>> all,int page,int pageSize) {
        int start=Math.min((page-1)*pageSize,all.size()),end=Math.min(start+pageSize,all.size());
        return Map.of("items",all.subList(start,end),"page",page,"pageSize",pageSize,"totalItems",all.size(),
                "totalPages",all.isEmpty()?0:(all.size()+pageSize-1)/pageSize);
    }
    private void validatePage(int page,int pageSize) {
        if(page<1||pageSize<1||pageSize>100) throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Tham số phân trang không hợp lệ");
    }
    private void validatePoi(String name,String description) {
        if(name==null||name.isBlank()||name.length()>255||description!=null&&description.length()>2000)
            throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Tên điểm bắt buộc, tối đa 255 ký tự; mô tả tối đa 2.000 ký tự",
                    Map.of("name","required, max 255","description","max 2000"));
    }
    private void validateContent(String title,String lang,String text) {
        if(title==null||title.isBlank()||title.length()>255||text==null||text.isBlank()||text.length()>10000)
            throw new ApiException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Dữ liệu nội dung không hợp lệ",
                    Map.of("title","required, max 255","textContent","required, max 10000"));
        if(!LanguageRegistry.isSupported(lang)) {
            var def = LanguageRegistry.findByCode(lang);
            String msg = (def != null && !def.enabled())
                    ? "Ngôn ngữ nguồn '" + lang + "' đã tạm dừng hoạt động hoặc chưa được kích hoạt"
                    : "Ngôn ngữ nguồn '" + lang + "' không được hệ thống hỗ trợ";
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_SOURCE_LANGUAGE",msg,
                    Map.of("sourceLang", lang == null ? "null" : lang));
        }
    }
    private String newQrCode() { byte[] bytes=new byte[18];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private ApiException poiMissing() { return new ApiException(HttpStatus.NOT_FOUND,"POI_NOT_FOUND","Không tìm thấy điểm tham quan"); }
    private ApiException contentMissing() { return new ApiException(HttpStatus.NOT_FOUND,"CONTENT_NOT_FOUND","Không tìm thấy nội dung"); }
}
