package vn.edu.cnpm.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="CONTENT_SERVICE_TOKEN=test-content-token")
class ContentContractTest {
    @Autowired TestRestTemplate client;
    @Autowired JdbcTemplate jdbc;

    @Test
    void poiCrudKeepsQrAndBlocksDeleteWhenAnyContentRowExists() {
        Map poi=createPoi("Museum "+UUID.randomUUID());
        String id=(String)poi.get("poiId"),qr=(String)poi.get("qrCode");
        assertThat(poi.get("poiId")).isNotNull();
        assertThat(qr).isNotBlank();

        HttpHeaders admin=adminHeaders();
        ResponseEntity<Map> updated=client.exchange("/api/v1/pois/{id}",HttpMethod.PUT,
                new HttpEntity<>(Map.of("name","Updated museum","description","Updated description"),admin),Map.class,id);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map)updated.getBody().get("data")).get("qrCode")).isEqualTo(qr);

        ResponseEntity<Map> forbidden=client.exchange("/api/v1/pois/{id}",HttpMethod.GET,
                new HttpEntity<>(headers("VISITOR")),Map.class,id);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> deleted=client.exchange("/api/v1/pois/{id}",HttpMethod.DELETE,new HttpEntity<>(admin),Void.class,id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> missing=client.exchange("/api/v1/pois/{id}",HttpMethod.GET,new HttpEntity<>(admin),Map.class,id);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("errorCode","POI_NOT_FOUND");
    }

    @Test
    void contentVersionsAreImmutableAndSoftDeletedVersionsRemainReadableInternally() {
        String poiId=(String)createPoi("Venue "+UUID.randomUUID()).get("poiId");
        HttpHeaders admin=adminHeaders();admin.set("X-User-Id","admin-user");admin.set("X-Correlation-ID","contract-test");
        ResponseEntity<Map> created=client.exchange("/api/v1/pois/{id}/contents",HttpMethod.POST,
                new HttpEntity<>(Map.of("title","Original","sourceLang","vi","textContent","Xin chào"),admin),Map.class,poiId);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map content=(Map)created.getBody().get("data");
        String contentId=(String)content.get("contentId");
        assertThat(content.get("version")).isEqualTo(1);
        assertThat(content.get("createdBy")).isEqualTo("admin-user");

        ResponseEntity<Map> version2=client.exchange("/api/v1/contents/{id}",HttpMethod.PUT,
                new HttpEntity<>(Map.of("baseVersion",1,"title","Updated","sourceLang","vi","textContent","Chào mừng"),admin),Map.class,contentId);
        assertThat(version2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map)version2.getBody().get("data")).get("version")).isEqualTo(2);

        ResponseEntity<Map> stale=client.exchange("/api/v1/contents/{id}",HttpMethod.PUT,
                new HttpEntity<>(Map.of("baseVersion",1,"title","Lost update","sourceLang","vi","textContent","Không được lưu"),admin),Map.class,contentId);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("errorCode","CONTENT_VERSION_CONFLICT");
        assertThat(stale.getBody().get("details")).isEqualTo(Map.of("currentVersion",2));

        ResponseEntity<Void> deleted=client.exchange("/api/v1/contents/{id}",HttpMethod.DELETE,new HttpEntity<>(admin),Void.class,contentId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        HttpHeaders internal=new HttpHeaders();internal.set("X-Service-Token","test-content-token");
        ResponseEntity<Map> exact=client.exchange("/internal/contents/{id}/versions/{version}",HttpMethod.GET,
                new HttpEntity<>(internal),Map.class,contentId,1);
        assertThat(exact.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map exactData=(Map)exact.getBody().get("data");
        assertThat(exactData.get("textContent")).isEqualTo("Xin chào");
        assertThat(exactData.get("isDeleted")).isEqualTo(true);

        ResponseEntity<Map> current=client.exchange("/internal/contents/{id}/current",HttpMethod.GET,
                new HttpEntity<>(internal),Map.class,contentId);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(current.getBody()).containsEntry("errorCode","CONTENT_NOT_FOUND");

        ResponseEntity<Map> noToken=client.exchange("/internal/contents/{id}/versions/{version}",HttpMethod.GET,
                HttpEntity.EMPTY,Map.class,contentId,1);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void poiDeleteConflictsWhenActiveContentExistsAndSucceedsWhenSoftDeleted() {
        String poiId=(String)createPoi("History "+UUID.randomUUID()).get("poiId");
        HttpHeaders admin=adminHeaders();admin.set("X-User-Id","admin-user");
        Map result=client.exchange("/api/v1/pois/{id}/contents",HttpMethod.POST,
                new HttpEntity<>(Map.of("title","History","sourceLang","vi","textContent","Text"),admin),Map.class,poiId).getBody();
        String contentId=(String)((Map)result.get("data")).get("contentId");

        // Khi nội dung còn đang hoạt động -> xóa điểm bị chặn
        ResponseEntity<Map> blocked=client.exchange("/api/v1/pois/{id}",HttpMethod.DELETE,new HttpEntity<>(admin),Map.class,poiId);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody()).containsEntry("errorCode","POI_HAS_CONTENT");

        // Xóa mềm nội dung
        client.exchange("/api/v1/contents/{id}",HttpMethod.DELETE,new HttpEntity<>(admin),Void.class,contentId);

        // Khi mọi nội dung đã xóa mềm -> xóa điểm thành công
        ResponseEntity<Void> deleted=client.exchange("/api/v1/pois/{id}",HttpMethod.DELETE,new HttpEntity<>(admin),Void.class,poiId);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Điểm tham quan đã bị xóa mềm và không còn tìm thấy qua GET
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM points_of_interest WHERE poi_id=?",Boolean.class,poiId)).isTrue();
        ResponseEntity<Map> getDeleted=client.exchange("/api/v1/pois/{id}",HttpMethod.GET,new HttpEntity<>(admin),Map.class,poiId);
        assertThat(getDeleted.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void languageConfigFiltersDisabledLanguagesByDefaultAndAllowsOptIn() {
        HttpHeaders admin = adminHeaders();
        // 1. Mặc định chỉ trả ngôn ngữ enabled=true
        ResponseEntity<Map> defaultResp = client.exchange("/api/v1/languages/config", HttpMethod.GET,
                new HttpEntity<>(admin), Map.class);
        assertThat(defaultResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data = (Map) defaultResp.getBody().get("data");
        java.util.List<Map> langs = (java.util.List<Map>) data.get("languages");
        assertThat(langs).allMatch(l -> Boolean.TRUE.equals(l.get("enabled")));
        assertThat(langs).noneMatch(l -> "es".equalsIgnoreCase(String.valueOf(l.get("code")))
                || "th".equalsIgnoreCase(String.valueOf(l.get("code")))
                || "ar".equalsIgnoreCase(String.valueOf(l.get("code"))));

        // 2. Có tham số includeDisabled=true hoặc all=true thì trả đầy đủ
        ResponseEntity<Map> fullResp = client.exchange("/api/v1/languages/config?includeDisabled=true", HttpMethod.GET,
                new HttpEntity<>(admin), Map.class);
        assertThat(fullResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        java.util.List<Map> fullLangs = (java.util.List<Map>) ((Map) fullResp.getBody().get("data")).get("languages");
        assertThat(fullLangs).anyMatch(l -> "es".equalsIgnoreCase(String.valueOf(l.get("code"))));
    }

    @Test
    void createContentRejectsDisabledOrUnsupportedLanguage() {
        String poiId = (String) createPoi("Venue " + UUID.randomUUID()).get("poiId");
        HttpHeaders admin = adminHeaders();
        admin.set("X-User-Id", "admin-user");

        // Gửi ngôn ngữ đã bị tắt (ví dụ es)
        ResponseEntity<Map> disabledLang = client.exchange("/api/v1/pois/{id}/contents", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Test", "sourceLang", "es", "textContent", "Contenido"), admin),
                Map.class, poiId);
        assertThat(disabledLang.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(disabledLang.getBody()).containsEntry("errorCode", "INVALID_SOURCE_LANGUAGE");

        // Gửi ngôn ngữ hoàn toàn không tồn tại
        ResponseEntity<Map> unknownLang = client.exchange("/api/v1/pois/{id}/contents", HttpMethod.POST,
                new HttpEntity<>(Map.of("title", "Test", "sourceLang", "xx", "textContent", "Unknown"), admin),
                Map.class, poiId);
        assertThat(unknownLang.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknownLang.getBody()).containsEntry("errorCode", "INVALID_SOURCE_LANGUAGE");
    }

    private Map createPoi(String name) {
        ResponseEntity<Map> response=client.exchange("/api/v1/pois",HttpMethod.POST,
                new HttpEntity<>(Map.of("name",name,"description","Description"),adminHeaders()),Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map)response.getBody().get("data");
    }
    private HttpHeaders adminHeaders(){return headers("CONTENT_ADMIN");}
    private HttpHeaders headers(String role){HttpHeaders h=new HttpHeaders();h.set("X-User-Role",role);return h;}
}
