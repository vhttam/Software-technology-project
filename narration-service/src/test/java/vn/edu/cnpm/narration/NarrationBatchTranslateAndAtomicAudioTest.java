package vn.edu.cnpm.narration;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.cnpm.narration.service.NarrationStore;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext
class NarrationBatchTranslateAndAtomicAudioTest {

    private static final MockWebServer contentServer = new MockWebServer();
    private static final MockWebServer ttsServer = new MockWebServer();

    static {
        contentServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() != null ? request.getPath() : "";
                if (path.contains("/internal/contents/lookup")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("""
                                    {
                                      "status": "success",
                                      "data": {
                                        "items": [
                                          {"contentId": "cnt-missing", "poiName": "Bảo tàng A", "title": "Gian A"},
                                          {"contentId": "cnt-same-source", "poiName": "Bảo tàng B", "title": "Gian B"},
                                          {"contentId": "cnt-already-done", "poiName": "Bảo tàng C", "title": "Gian C"}
                                        ]
                                      }
                                    }
                                    """);
                }
                if (path.contains("/internal/contents/cnt-missing/current")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"status\":\"success\",\"data\":{\"contentId\":\"cnt-missing\",\"version\":1,\"sourceLang\":\"vi\"}}");
                }
                if (path.contains("/internal/contents/cnt-same-source/current")) {
                    // sourceLang is 'en', so translating into 'en' will be skipped!
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"status\":\"success\",\"data\":{\"contentId\":\"cnt-same-source\",\"version\":1,\"sourceLang\":\"en\"}}");
                }
                if (path.contains("/internal/contents/cnt-already-done/current")) {
                    return new MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"status\":\"success\",\"data\":{\"contentId\":\"cnt-already-done\",\"version\":1,\"sourceLang\":\"vi\"}}");
                }
                return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                        .setBody("{\"status\":\"success\",\"data\":{}}");
            }
        });

        ttsServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""
                                {
                                  "status": "success",
                                  "data": {
                                    "supportedVoices": [
                                      {"lang": "en", "voices": ["en-US-Journey-F"]},
                                      {"lang": "vi", "voices": ["vi-VN-Standard-A"]}
                                    ]
                                  }
                                }
                                """);
            }
        });

        try {
            contentServer.start();
            ttsServer.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void endpoints(DynamicPropertyRegistry registry) {
        registry.add("CONTENT_SERVICE_URL", () -> contentServer.url("/").toString());
        registry.add("TTS_SERVICE_URL", () -> ttsServer.url("/").toString());
        registry.add("NARRATION_SERVICE_TOKEN", () -> "narration-test-token");
        registry.add("GATEWAY_SERVICE_TOKEN", () -> "gateway-test-token");
    }

    @AfterAll
    static void stopServers() throws IOException {
        contentServer.shutdown();
        ttsServer.shutdown();
    }

    @Autowired
    private TestRestTemplate client;

    @Autowired
    private NarrationStore store;

    @Test
    void batchTranslateCreatesJobsForMissingLanguagesSkipsExistingAndSourceAndSupportsCancellation() {
        // Prepare: cnt-already-done already has published language 'en'
        Map jobOld = store.createJob("admin-root", "key-" + UUID.randomUUID(), "hash-" + UUID.randomUUID(),
                "cnt-already-done", 1, List.of(new NarrationStore.JobTargetInput("en", "en-US-Journey-F")), null, "corr-batch");
        String oldTargetId = (String) ((Map) ((List) jobOld.get("targets")).get(0)).get("targetId");
        store.markRequested(oldTargetId);
        store.applyTtsCompleted((String) jobOld.get("jobId"), oldTargetId, "trans-old", "audio-old");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "CONTENT_ADMIN");
        headers.set("X-User-Id", "admin-batch");

        // Request batch translation to 'en'
        ResponseEntity<Map> startRes = client.exchange(
                "/api/v1/jobs/batch-translate",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("targetLang", "en", "voiceId", "en-US-Journey-F"), headers),
                Map.class
        );
        assertThat(startRes.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map data = (Map) startRes.getBody().get("data");
        String batchId = (String) data.get("batchId");
        assertThat(batchId).isNotBlank();

        // 3 total: cnt-same-source is skipped (sourceLang==en), cnt-already-done is skipped (already published),
        // only cnt-missing is queued
        assertThat(data.get("totalContents")).isEqualTo(3);
        assertThat(data.get("skippedContents")).isEqualTo(2);
        assertThat(data.get("queuedContents")).isEqualTo(1);

        // Check getBatchTranslate
        ResponseEntity<Map> getRes = client.exchange(
                "/api/v1/jobs/batch-translate/" + batchId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) getRes.getBody().get("data")).get("batchId")).isEqualTo(batchId);

        // Cancel batch translation
        ResponseEntity<Map> cancelRes = client.exchange(
                "/api/v1/jobs/batch-translate/" + batchId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(cancelRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) cancelRes.getBody().get("data")).get("status")).isEqualTo("CANCELLED");
    }

    @Test
    void atomicAudioReplacementServesOldAudioUntilNewPublishedAndRetainsOldOnFailure() {
        String contentId = "cnt-atomic-" + UUID.randomUUID().toString().substring(0, 8);

        // Step 1: Initial Job 1 creates and successfully publishes audio-v1-original
        Map job1 = store.createJob("admin-1", "idem-1-" + UUID.randomUUID(), "hash-1",
                contentId, 1, List.of(new NarrationStore.JobTargetInput("en", "en-US-Journey-F")), null, "corr-1");
        String job1Id = (String) job1.get("jobId");
        String target1Id = (String) ((Map) ((List) job1.get("targets")).get(0)).get("targetId");

        store.markRequested(target1Id);
        store.applyTtsCompleted(job1Id, target1Id, "trans-v1-orig", "audio-v1-original");

        // Verify initial state
        Map<String, Object> targetInitial = store.publishedTarget(contentId, "en");
        assertThat(targetInitial).isNotNull();
        assertThat(targetInitial.get("audioId")).isEqualTo("audio-v1-original");

        // Step 2: A re-synthesis Job 2 is started for the same version (e.g. user requested regenerate audio)
        Map job2 = store.createJob("admin-1", "idem-2-" + UUID.randomUUID(), "hash-2",
                contentId, 1, List.of(new NarrationStore.JobTargetInput("en", "en-US-Journey-F")), null, "corr-2");
        String job2Id = (String) job2.get("jobId");
        String target2Id = (String) ((Map) ((List) job2.get("targets")).get(0)).get("targetId");
        store.markRequested(target2Id);

        // While Job 2 is in-progress (TRANSLATING / SYNTHESIZING), publishedTarget must STILL return audio-v1-original
        Map<String, Object> targetDuringJob2 = store.publishedTarget(contentId, "en");
        assertThat(targetDuringJob2).isNotNull();
        assertThat(targetDuringJob2.get("audioId")).isEqualTo("audio-v1-original");

        // Step 3: Job 2 fails (e.g. TTS network failure)
        store.applyFailure(job2Id, target2Id, "TTS_SYNTHESIS_FAILED");

        // After failure, publishedTarget must STILL retain audio-v1-original (no disruption to visitors!)
        Map<String, Object> targetAfterFailure = store.publishedTarget(contentId, "en");
        assertThat(targetAfterFailure).isNotNull();
        assertThat(targetAfterFailure.get("audioId")).isEqualTo("audio-v1-original");

        // Step 4: Re-synthesis Job 3 is launched and succeeds
        Map job3 = store.createJob("admin-1", "idem-3-" + UUID.randomUUID(), "hash-3",
                contentId, 1, List.of(new NarrationStore.JobTargetInput("en", "en-US-Journey-F")), null, "corr-3");
        String job3Id = (String) job3.get("jobId");
        String target3Id = (String) ((Map) ((List) job3.get("targets")).get(0)).get("targetId");
        store.markRequested(target3Id);
        store.applyTtsCompleted(job3Id, target3Id, "trans-v1-new", "audio-v1-new");

        // Now, publishedTarget atomically switches to audio-v1-new
        Map<String, Object> targetFinal = store.publishedTarget(contentId, "en");
        assertThat(targetFinal).isNotNull();
        assertThat(targetFinal.get("audioId")).isEqualTo("audio-v1-new");
    }
}
