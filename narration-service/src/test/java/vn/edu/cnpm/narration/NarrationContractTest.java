package vn.edu.cnpm.narration;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.cnpm.narration.service.NarrationStore;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class NarrationContractTest {
    static final MockWebServer contentServer=new MockWebServer();
    static final MockWebServer ttsServer=new MockWebServer();
    static {
        try{contentServer.start();ttsServer.start();}catch(IOException ex){throw new ExceptionInInitializerError(ex);}
    }
    @DynamicPropertySource static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("CONTENT_SERVICE_URL",()->contentServer.url("/").toString());
        registry.add("TTS_SERVICE_URL",()->ttsServer.url("/").toString());
        registry.add("NARRATION_SERVICE_TOKEN",()->"narration-test-token");
        registry.add("GATEWAY_SERVICE_TOKEN",()->"gateway-test-token");
    }
    @AfterAll static void stopServers() throws IOException {contentServer.shutdown();ttsServer.shutdown();}
    @Autowired TestRestTemplate client;@Autowired NarrationStore store;@Autowired JdbcTemplate jdbc;

    @Test void createJobUsesCurrentContentVersionAndIdempotencyKey(){
        enqueueDependencies();
        HttpHeaders headers=admin();headers.set("Idempotency-Key","job-key-"+UUID.randomUUID());headers.set("X-User-Id","admin-1");
        Map<String,Object> body=request("content-a",null,"en","en-US-Journey-F");
        ResponseEntity<Map> created=client.exchange("/api/v1/jobs",HttpMethod.POST,new HttpEntity<>(body,headers),Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map job=(Map)created.getBody().get("data");
        assertThat(job.get("version")).isEqualTo(1);assertThat(job.get("status")).isEqualTo("PENDING");
        Map target=(Map)((List)job.get("targets")).get(0);assertThat(target.get("status")).isEqualTo("PENDING");

        ResponseEntity<Map> replay=client.exchange("/api/v1/jobs",HttpMethod.POST,new HttpEntity<>(body,headers),Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(((Map)replay.getBody().get("data")).get("jobId")).isEqualTo(job.get("jobId"));

        HttpHeaders reused=admin();reused.set("Idempotency-Key",headers.getFirst("Idempotency-Key"));reused.set("X-User-Id","admin-1");
        ResponseEntity<Map> collision=client.exchange("/api/v1/jobs",HttpMethod.POST,
                new HttpEntity<>(request("different-content",null,"en","en-US-Journey-F"),reused),Map.class);
        assertThat(collision.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(collision.getBody()).containsEntry("errorCode","IDEMPOTENCY_KEY_REUSED");
    }

    @Test void createJobRejectsDisabledOrUnsupportedTargetLanguage(){
        enqueueDependencies();
        HttpHeaders headers=admin();headers.set("Idempotency-Key","dis-key-"+UUID.randomUUID());headers.set("X-User-Id","admin-1");
        Map<String,Object> body=request("content-a",null,"es","es-ES-Standard-A");
        ResponseEntity<Map> resp=client.exchange("/api/v1/jobs",HttpMethod.POST,new HttpEntity<>(body,headers),Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).containsEntry("errorCode","LANGUAGE_NOT_ENABLED");
    }

    @Test void requiredKeyTargetValidationAndCancelAreContractual(){
        ResponseEntity<Map> missingKey=client.exchange("/api/v1/jobs",HttpMethod.POST,
                new HttpEntity<>(request("content-a",null,"en","en-US-Journey-F"),admin()),Map.class);
        assertThat(missingKey.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingKey.getBody()).containsEntry("errorCode","IDEMPOTENCY_KEY_REQUIRED");

        enqueueDependencies();
        HttpHeaders headers=admin();headers.set("Idempotency-Key","cancel-key-"+UUID.randomUUID());headers.set("X-User-Id","admin-2");
        Map body=request("content-a",null,"en","en-US-Journey-F");
        Map job=(Map)client.exchange("/api/v1/jobs",HttpMethod.POST,new HttpEntity<>(body,headers),Map.class).getBody().get("data");
        String id=String.valueOf(job.get("jobId"));
        ResponseEntity<Map> canceled=client.postForEntity("/api/v1/jobs/{id}/cancel",new HttpEntity<>(admin()),Map.class,id);
        assertThat(canceled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map)canceled.getBody().get("data")).get("status")).isEqualTo("CANCELLED");
        assertThat(((Map)((List)((Map)canceled.getBody().get("data")).get("targets")).get(0)).get("status")).isEqualTo("CANCELLED");
        ResponseEntity<Map> again=client.postForEntity("/api/v1/jobs/{id}/cancel",new HttpEntity<>(admin()),Map.class,id);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("errorCode","JOB_ALREADY_FINISHED");
    }

    @Test void eventOrderCannotReviveFailedOrCancelledTargets(){
        Map job=store.createJob("admin-3","state-key-"+UUID.randomUUID(),"state-hash-"+UUID.randomUUID(),
                "state-content",1,List.of(new NarrationStore.JobTargetInput("en","en-US-Journey-F")),null,"corr-state");
        String jobId=String.valueOf(job.get("jobId"));
        Map target=(Map)((List)job.get("targets")).get(0);String targetId=String.valueOf(target.get("targetId"));
        store.applyFailure(jobId,targetId,"PROVIDER_ERROR");
        store.applyTtsCompleted(jobId,targetId,"translation-x","audio-x");
        Map failed=store.getJob(jobId);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(((Map)((List)failed.get("targets")).get(0)).get("status")).isEqualTo("FAILED");

        Map job2=store.createJob("admin-3","state-key2-"+UUID.randomUUID(),"state-hash2-"+UUID.randomUUID(),
                "state-content",1,List.of(new NarrationStore.JobTargetInput("fr","fr-FR-Standard-A")),null,"corr-state");
        String jobId2=String.valueOf(job2.get("jobId"));
        Map target2=(Map)((List)job2.get("targets")).get(0);
        store.markRequested(String.valueOf(target2.get("targetId")));
        store.applyTtsCompleted(jobId2,String.valueOf(target2.get("targetId")),"translation-y","audio-y");
        Map completed=store.getJob(jobId2);
        assertThat(completed.get("status")).isEqualTo("COMPLETED");
        assertThat(((Map)((List)completed.get("targets")).get(0)).get("status")).isEqualTo("PUBLISHED");
    }

    @Test void timeoutFailsStalledTargetAndPublishedLookupReturnsNotFoundDetails(){
        Map job=store.createJob("admin-4","timeout-key-"+UUID.randomUUID(),"timeout-hash-"+UUID.randomUUID(),
                "timeout-content",1,List.of(new NarrationStore.JobTargetInput("en","en-US-Journey-F")),null,"corr-timeout");
        String jobId=String.valueOf(job.get("jobId"));Map target=(Map)((List)job.get("targets")).get(0);
        String targetId=String.valueOf(target.get("targetId"));
        String old=Instant.now().minus(Duration.ofMinutes(20)).toString();
        storeTimeoutTarget(targetId,old);
        Map expired=store.timeoutJob(jobId,Duration.ofMinutes(15),Duration.ofMinutes(15));
        assertThat(expired.get("status")).isEqualTo("FAILED");
        assertThat(((Map)((List)expired.get("targets")).get(0)).get("errorCode")).isEqualTo("TIMEOUT");

        HttpHeaders internal=new HttpHeaders();internal.set("X-Service-Token","narration-test-token");
        ResponseEntity<Map> missing=client.exchange("/internal/narrations/en?contentId=timeout-content",HttpMethod.GET,new HttpEntity<>(internal),Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("errorCode","NARRATION_NOT_FOUND");
        assertThat(missing.getBody().get("details")).isEqualTo(Map.of("availableLangs",List.of()));
    }

    private void storeTimeoutTarget(String targetId,String updatedAt){
        // Test sets an old timestamp to exercise the scheduled timeout boundary deterministically.
        jdbc.update("UPDATE job_targets SET updated_at=? WHERE target_id=?",updatedAt,targetId);
    }
    private void enqueueDependencies(){
        contentServer.enqueue(json("{\"status\":\"success\",\"data\":{\"contentId\":\"content-a\",\"version\":1,\"sourceLang\":\"vi\"}}"));
        ttsServer.enqueue(json("{\"status\":\"success\",\"data\":{\"supportedVoices\":[{\"lang\":\"en\",\"voices\":[\"en-US-Journey-F\"]},{\"lang\":\"vi\",\"voices\":[\"vi-VN-Standard-A\"]}]}}"));
    }
    private HttpHeaders admin(){HttpHeaders h=new HttpHeaders();h.set("X-User-Role","CONTENT_ADMIN");return h;}
    private Map<String,Object> request(String contentId,String retry,String lang,String voice){
        Map<String,Object> r=new LinkedHashMap<>();r.put("contentId",contentId);if(retry!=null)r.put("retryOfJobId",retry);
        r.put("targets",List.of(Map.of("lang",lang,"voiceId",voice)));return r;
    }
    private MockResponse json(String value){return new MockResponse().setHeader("Content-Type","application/json").setBody(value);}
}
