package vn.edu.cnpm.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ListeningEventsAndAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM listening_events");
    }

    @Test
    void testRecordListeningEvent_ValidationSuccess() throws Exception {
        Map<String, Object> req = Map.of(
                "visitorId", "visitor-123",
                "poiId", "poi-456",
                "lang", "ja",
                "eventType", "STARTED",
                "listenedSeconds", 0,
                "durationSeconds", 120
        );

        mockMvc.perform(post("/api/v1/client/listening-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.status").value("RECORDED"))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void testRecordListeningEvent_InvalidEventType() throws Exception {
        Map<String, Object> req = Map.of(
                "visitorId", "visitor-123",
                "poiId", "poi-456",
                "lang", "ja",
                "eventType", "INVALID_TYPE"
        );

        mockMvc.perform(post("/api/v1/client/listening-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_EVENT_TYPE"));
    }

    @Test
    void testGetListeningAnalytics_AggregationCalculation() throws Exception {
        // Seed 4 events: 2 STARTED (vi, en), 1 COMPLETED (vi), 1 STARTED (ja)
        String t1 = "2026-11-14T07:00:00Z"; // 14:00 Asia/Ho_Chi_Minh
        String t2 = "2026-11-14T07:15:00Z"; // 14:15 Asia/Ho_Chi_Minh
        String t3 = "2026-11-14T07:30:00Z"; // 14:30 Asia/Ho_Chi_Minh

        jdbc.update("INSERT INTO listening_events (id, visitor_id, poi_id, lang, event_type, listened_seconds, duration_seconds, occurred_at) " +
                "VALUES ('e1', 'v1', 'poi1', 'vi', 'STARTED', 0, 100, ?)", t1);
        jdbc.update("INSERT INTO listening_events (id, visitor_id, poi_id, lang, event_type, listened_seconds, duration_seconds, occurred_at) " +
                "VALUES ('e2', 'v1', 'poi1', 'vi', 'COMPLETED', 100, 100, ?)", t2);
        jdbc.update("INSERT INTO listening_events (id, visitor_id, poi_id, lang, event_type, listened_seconds, duration_seconds, occurred_at) " +
                "VALUES ('e3', 'v2', 'poi1', 'en', 'STARTED', 0, 100, ?)", t3);

        // Call admin analytics endpoint with CONTENT_ADMIN role header
        mockMvc.perform(get("/api/v1/analytics/listening")
                        .header("X-User-Role", "CONTENT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.totalEvents").value(3))
                .andExpect(jsonPath("$.data.startedCount").value(2))
                .andExpect(jsonPath("$.data.completedCount").value(1))
                .andExpect(jsonPath("$.data.completionRate").value(50.0)) // 1 completed / 2 started = 50%
                .andExpect(jsonPath("$.data.languageShares", hasSize(2)))
                .andExpect(jsonPath("$.data.peakInterval").isNotEmpty());
    }

    @Test
    void testGetListeningAnalytics_RoleForbiddenWithoutAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/listening")
                        .header("X-User-Role", "VISITOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }
}
