package vn.edu.cnpm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gateway.auth-service-url=http://127.0.0.1:1",
        "gateway.content-service-url=http://127.0.0.1:1"
})
@AutoConfigureWebTestClient
class HealthWithoutDependenciesTest {
    @Autowired
    private WebTestClient client;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void applicationStartsAndHealthWorksWhenAuthAndContentAreOffline() throws Exception {
        var response = client.get().uri("/health").exchange()
                .expectStatus().isOk().expectBody().returnResult();

        assertThat(objectMapper.readTree(response.getResponseBodyContent()).get("status").asText()).isEqualTo("UP");
        UUID.fromString(response.getResponseHeaders().getFirst("X-Correlation-ID"));
    }
}
