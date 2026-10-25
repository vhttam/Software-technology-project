package vn.edu.cnpm.translation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"NARRATION_SERVICE_TOKEN=test-narration-token","TTS_SERVICE_TOKEN=test-tts-token"})
class TranslationContractTest {
    @Autowired TestRestTemplate client;@Autowired JdbcTemplate jdbc;
    @Test void internalTranslationReturnsPersistedTextAndRequiresCallerToken(){
        String id=UUID.randomUUID().toString(),content=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO translations(translation_id,content_id,version,lang,text_content,created_at) VALUES(?,?,1,'en','Welcome','2026-10-16T00:00:00Z')",id,content);
        HttpHeaders headers=new HttpHeaders();headers.set("X-Service-Token","test-narration-token");
        ResponseEntity<Map> response=client.exchange("/internal/translations/{id}",HttpMethod.GET,new HttpEntity<>(headers),Map.class,id);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("success");
        assertThat(response.getBody().get("data")).isEqualTo(Map.of("translationId",id,"contentId",content,"version",1,
                "lang","en","textContent","Welcome","createdAt","2026-10-16T00:00:00Z"));
        ResponseEntity<Map> denied=client.exchange("/internal/translations/{id}",HttpMethod.GET,HttpEntity.EMPTY,Map.class,id);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(denied.getBody().get("status")).isEqualTo("error");
    }

    @Autowired
    vn.edu.cnpm.translation.provider.TranslationProvider provider;

    @Test
    void targetSameAsSourceReturnsOriginalText() {
        String original = "Văn bản gốc tiếng Việt";
        String result = provider.translate(original, "vi", "vi");
        assertThat(result).isEqualTo(original);
    }
}
