package vn.edu.cnpm.tts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.cnpm.tts.provider.TtsProvider;
import vn.edu.cnpm.tts.storage.SignedUrlService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"NARRATION_SERVICE_TOKEN=test-narration-token","OBJECT_STORAGE_PUBLIC_URL=http://localhost:8085","STORAGE_SIGNING_SECRET=test-only-signing-secret"})
class TtsContractTest {
    @Autowired TestRestTemplate client;@Autowired JdbcTemplate jdbc;@Autowired SignedUrlService urls;@Autowired TtsProvider provider;
    @Test void voiceCatalogAndSignedAudioLinkFollowContract(){
        HttpHeaders headers=new HttpHeaders();headers.set("X-Service-Token","test-narration-token");
        ResponseEntity<Map> voices=client.exchange("/internal/voices",HttpMethod.GET,new HttpEntity<>(headers),Map.class);
        assertThat(voices.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data=(Map)voices.getBody().get("data");
        assertThat(data.get("supportedVoices").toString()).contains("en-US-Journey-F","fr-FR-Standard-A");

        String audioId=UUID.randomUUID().toString(),translation=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO audios(audio_id,translation_id,voice_id,object_key,duration_seconds,size_bytes,created_at) VALUES(?,?,'en-US-Journey-F','audio/test.mp3',2,8,'2026-10-16T00:00:00Z')",audioId,translation);
        ResponseEntity<Map> signed=client.exchange("/internal/audios/{id}/signed-url",HttpMethod.GET,new HttpEntity<>(headers),Map.class,audioId);
        assertThat(signed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map signedData=(Map)signed.getBody().get("data");
        assertThat(signedData.keySet()).containsExactlyInAnyOrder("audioUrl","expiresAt");
        Instant expiry=Instant.parse((String)signedData.get("expiresAt"));
        assertThat(expiry).isAfter(Instant.now().plusSeconds(3500)).isBefore(Instant.now().plusSeconds(3700));
        String url=(String)signedData.get("audioUrl");
        assertThat(url).contains("signature=");
        String signature=url.substring(url.indexOf("signature=")+"signature=".length());
        assertThat(urls.isValid("audio/test.mp3",expiry.getEpochSecond(),signature)).isTrue();
        assertThat(urls.isValid("audio/test.mp3",expiry.getEpochSecond(),"tampered")).isFalse();

        byte[] audio = provider.synthesize("welcome","en","en-US-Journey-F");
        assertThat(audio).isNotEmpty();
        assertThat(audio[0]).isEqualTo((byte) 0xFF);
        assertThat(audio[1]).isEqualTo((byte) 0xFB);
        assertThat(jdbc.queryForList("SELECT * FROM audios WHERE audio_id=?",audioId).get(0))
                .doesNotContainKey("audio_url").doesNotContainKey("signed_url");
    }
}
