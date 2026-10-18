package vn.edu.cnpm.narration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.cnpm.narration.messaging.NarrationResultConsumer;
import vn.edu.cnpm.narration.service.NarrationStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.messaging.enabled=true"
        })
class RabbitMqEndToEndFlowTest {

    static final MockWebServer contentServer = new MockWebServer();
    static final MockWebServer ttsServer = new MockWebServer();

    static {
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
        registry.add("NARRATION_SERVICE_TOKEN", () -> "narration-flow-token");
        registry.add("GATEWAY_SERVICE_TOKEN", () -> "gateway-flow-token");
    }

    @AfterAll
    static void stopServers() throws IOException {
        contentServer.shutdown();
        ttsServer.shutdown();
    }

    @Autowired
    TestRestTemplate client;
    @Autowired
    NarrationStore store;
    @Autowired
    NarrationResultConsumer consumer;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void endToEndSixLanguageFlowFromRequestedToPublished() throws Exception {
        // Enqueue content metadata and voice catalog with 6 enabled languages
        contentServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"success\",\"data\":{\"contentId\":\"content-6lang\",\"version\":1,\"sourceLang\":\"vi\"}}"));

        ttsServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "status":"success",
                          "data":{
                            "supportedVoices":[
                              {"lang":"vi","voices":["vi-VN-Standard-A"]},
                              {"lang":"en","voices":["en-US-Journey-F"]},
                              {"lang":"fr","voices":["fr-FR-Standard-A"]},
                              {"lang":"ja","voices":["ja-JP-Standard-A"]},
                              {"lang":"zh","voices":["zh-CN-Standard-A"]},
                              {"lang":"ko","voices":["ko-KR-Standard-A"]}
                            ]
                          }
                        }
                        """));

        Map<String, String> voiceByLang = Map.of(
                "vi", "vi-VN-Standard-A",
                "en", "en-US-Journey-F",
                "fr", "fr-FR-Standard-A",
                "ja", "ja-JP-Standard-A",
                "zh", "zh-CN-Standard-A",
                "ko", "ko-KR-Standard-A"
        );
        List<Map<String, String>> targets = new ArrayList<>();
        for (Map.Entry<String, String> entry : voiceByLang.entrySet()) {
            targets.add(Map.of("lang", entry.getKey(), "voiceId", entry.getValue()));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "CONTENT_ADMIN");
        headers.set("X-User-Id", "admin-e2e");
        String idempotencyKey = "e2e-key-" + UUID.randomUUID();
        headers.set("Idempotency-Key", idempotencyKey);

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("contentId", "content-6lang");
        reqBody.put("targets", targets);

        // 1. Submit narration job via API
        ResponseEntity<Map> createdResp = client.exchange("/api/v1/jobs", HttpMethod.POST, new HttpEntity<>(reqBody, headers), Map.class);
        assertThat(createdResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        Map jobData = (Map) createdResp.getBody().get("data");
        String jobId = String.valueOf(jobData.get("jobId"));
        assertThat(jobData.get("status")).isEqualTo("PENDING");

        List<Map<String, Object>> targetList = (List<Map<String, Object>>) jobData.get("targets");
        assertThat(targetList).hasSize(6);

        // 2. Mark requested (triggers transition to PROCESSING and target TRANSLATING)
        for (Map<String, Object> t : targetList) {
            String targetId = String.valueOf(t.get("targetId"));
            store.markRequested(targetId);
        }

        Map inFlightJob = store.getJob(jobId);
        assertThat(inFlightJob.get("status")).isEqualTo("PROCESSING");
        List<Map<String, Object>> inFlightTargets = (List<Map<String, Object>>) inFlightJob.get("targets");
        for (Map<String, Object> t : inFlightTargets) {
            assertThat(t.get("status")).isEqualTo("TRANSLATING");
        }

        Channel mockChannel = Mockito.mock(Channel.class);
        long deliveryTag = 100L;

        // 3. For each of the 6 languages, simulate translation.completed event
        for (Map<String, Object> t : inFlightTargets) {
            String targetId = String.valueOf(t.get("targetId"));
            String lang = String.valueOf(t.get("lang"));
            String translationId = UUID.randomUUID().toString();

            Map<String, Object> eventPayload = Map.of(
                    "jobId", jobId,
                    "targetId", targetId,
                    "contentId", "content-6lang",
                    "version", 1,
                    "lang", lang,
                    "translationId", translationId
            );

            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("eventId", UUID.randomUUID().toString());
            props.setHeader("correlationId", "corr-" + jobId);
            props.setReceivedRoutingKey("translation.completed");
            props.setDeliveryTag(++deliveryTag);

            Message msg = new Message(mapper.writeValueAsString(eventPayload).getBytes(StandardCharsets.UTF_8), props);

            consumer.translationCompleted(msg, mockChannel);
            verify(mockChannel).basicAck(eq(deliveryTag), anyBoolean());

            Map currentJob = store.getJob(jobId);
            Map<String, Object> updatedTarget = ((List<Map<String, Object>>) currentJob.get("targets")).stream()
                    .filter(x -> targetId.equals(String.valueOf(x.get("targetId"))))
                    .findFirst().orElseThrow();
            assertThat(updatedTarget.get("status")).isEqualTo("SYNTHESIZING");
        }

        // 4. For each of the 6 languages, simulate tts.completed event
        for (Map<String, Object> t : inFlightTargets) {
            String targetId = String.valueOf(t.get("targetId"));
            String lang = String.valueOf(t.get("lang"));
            String audioId = UUID.randomUUID().toString();

            Map<String, Object> eventPayload = Map.of(
                    "jobId", jobId,
                    "targetId", targetId,
                    "translationId", UUID.randomUUID().toString(),
                    "audioId", audioId
            );

            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("eventId", UUID.randomUUID().toString());
            props.setHeader("correlationId", "corr-" + jobId);
            props.setReceivedRoutingKey("tts.completed");
            props.setDeliveryTag(++deliveryTag);

            Message msg = new Message(mapper.writeValueAsString(eventPayload).getBytes(StandardCharsets.UTF_8), props);

            consumer.result(msg, mockChannel);
            verify(mockChannel).basicAck(eq(deliveryTag), anyBoolean());
        }

        // 5. Verify the entire job has reached COMPLETED with all 6 targets PUBLISHED
        Map finishedJob = store.getJob(jobId);
        assertThat(finishedJob.get("status")).isEqualTo("COMPLETED");

        List<Map<String, Object>> finishedTargets = (List<Map<String, Object>>) finishedJob.get("targets");
        assertThat(finishedTargets).hasSize(6);
        for (Map<String, Object> t : finishedTargets) {
            assertThat(t.get("status")).isEqualTo("PUBLISHED");
            String targetId = String.valueOf(t.get("targetId"));
            Map<String, Object> dbRow = jdbc.queryForMap("SELECT audio_id, translation_id FROM job_targets WHERE target_id=?", targetId);
            assertThat(dbRow.get("audio_id")).isNotNull();
            assertThat(dbRow.get("translation_id")).isNotNull();
        }

        // 6. Query job via API and verify response format
        ResponseEntity<Map> apiGetResp = client.exchange("/api/v1/jobs/{id}", HttpMethod.GET, new HttpEntity<>(headers), Map.class, jobId);
        assertThat(apiGetResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map apiJob = (Map) apiGetResp.getBody().get("data");
        assertThat(apiJob.get("status")).isEqualTo("COMPLETED");
        List<Map> apiTargets = (List<Map>) apiJob.get("targets");
        assertThat(apiTargets).hasSize(6);
        for (Map t : apiTargets) {
            assertThat(t.get("status")).isEqualTo("PUBLISHED");
        }
    }
}
