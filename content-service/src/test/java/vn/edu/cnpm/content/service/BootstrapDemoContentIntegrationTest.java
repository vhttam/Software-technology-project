package vn.edu.cnpm.content.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapDemoContentIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TestRestTemplate client;

    @Test
    void testBootstrapDemoContentLeavesDatabaseCleanWithoutDemoPois() throws Exception {
        BootstrapDemoContent bootstrap = new BootstrapDemoContent();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("DEMO_MODE", "true");

        bootstrap.seedDemoContent(jdbc, env).run(null);

        // Verify no demo POIs created
        List<Map<String, Object>> pois = jdbc.queryForList(
                "SELECT poi_id, name, qr_code FROM points_of_interest WHERE qr_code IN ('POI-DEMO-001', 'POI-DEMO-002')");
        assertThat(pois).isEmpty();
    }
}
