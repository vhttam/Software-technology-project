package vn.sgu.gateway.client;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientNarrationGatewayControllerTest {
    private static final String CORRELATION_ID = "corr-123";

    @Test
    void listAggregatesPoiAndPublishedLanguagesAndForwardsTrustedHeaders() {
        List<org.springframework.web.reactive.function.client.ClientRequest> requests = new CopyOnWriteArrayList<>();
        ExchangeFunction exchange = request -> {
            requests.add(request);
            if (request.url().getPath().contains("/internal/pois/")) {
                return json(HttpStatus.OK, """
                        {"status":"success","data":{"poiId":"poi-1","poiName":"Museum","contentId":"content-1","contentTitle":"History","isDeleted":false}}
                        """);
            }
            return json(HttpStatus.OK, """
                    {"status":"success","data":{"narrations":[{"lang":"en","version":4},{"lang":"vi","version":3}]}}
                    """);
        };
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.list("qr-1", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data().poiId()).isEqualTo("poi-1");
        assertThat(result.data().contentTitle()).isEqualTo("History");
        assertThat(result.data().narrations()).containsExactly(
                new ClientNarrationGatewayController.LanguageView("en", 4),
                new ClientNarrationGatewayController.LanguageView("vi", 3));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).headers().getFirst("X-Service-Token")).isEqualTo("internal-secret");
        assertThat(requests.get(1).headers().getFirst("X-Gateway-Service-Token")).isEqualTo("internal-secret");
        assertThat(requests).allSatisfy(request ->
                assertThat(request.headers().getFirst("X-Correlation-ID")).isEqualTo(CORRELATION_ID));
    }

    @Test
    void getAggregatesPoiAndNarrationArtifact() {
        ExchangeFunction exchange = request -> request.url().getPath().contains("/internal/pois/")
                ? json(HttpStatus.OK, """
                    {"status":"success","data":{"poiId":"poi-2","poiName":"Garden","contentId":"content-2","contentTitle":"Plants","isDeleted":false}}
                    """)
                : json(HttpStatus.OK, """
                    {"status":"success","data":{"lang":"fr","version":7,"subtitle":"Bonjour","audioUrl":"https://audio.example/signed","audioUrlExpiresAt":"2026-10-09T12:00:00Z"}}
                    """);
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.get("qr-2", "fr", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.data().poiId()).isEqualTo("poi-2");
        assertThat(result.data().lang()).isEqualTo("fr");
        assertThat(result.data().version()).isEqualTo(7);
        assertThat(result.data().subtitle()).isEqualTo("Bonjour");
        assertThat(result.data().audioUrl()).isEqualTo("https://audio.example/signed");
    }

    @Test
    void mockModeReturnsSampleAndMapsMissingPoi() {
        ClientNarrationGatewayController controller = controller(request -> json(HttpStatus.OK,
                "{\"status\":\"success\",\"data\":{\"narrations\":[]}}"), true);
        var sample = controller.list("qr-sample", exchangeWithCorrelation()).block(Duration.ofSeconds(1));
        assertThat(sample.data().poiId()).isEqualTo("poi-demo");

        assertThatThrownBy(() -> controller.list("missing-qr", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("POI_NOT_FOUND");
                });
    }

    @Test
    void downstreamNotFoundRemainsNotFoundAndServerFailureBecomesUnavailable() {
        ClientNarrationGatewayController notFoundController = controller(
                request -> json(HttpStatus.NOT_FOUND, "{}"), false);
        assertThatThrownBy(() -> notFoundController.list("qr-1", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("POI_NOT_FOUND");
                });

        ClientNarrationGatewayController unavailableController = controller(
                request -> json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"), false);
        assertThatThrownBy(() -> unavailableController.list("qr-1", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.errorCode()).isEqualTo("CONTENT_SERVICE_UNAVAILABLE");
                });
    }

    @Test
    void emptyDownstreamEnvelopeIsRejectedAsUnavailable() {
        ClientNarrationGatewayController controller = controller(request ->
                json(HttpStatus.OK, "{\"status\":\"success\",\"data\":null}"), false);

        assertThatThrownBy(() -> controller.list("qr-1", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.errorCode()).isEqualTo("CONTENT_SERVICE_UNAVAILABLE");
                });
    }

    private static ClientNarrationGatewayController controller(ExchangeFunction exchange, boolean mocksEnabled) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new ClientNarrationGatewayController(builder, "http://content.test", "http://narration.test",
                "internal-secret", mocksEnabled);
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static org.springframework.web.server.ServerWebExchange exchangeWithCorrelation() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/test")
                .header("X-Correlation-ID", CORRELATION_ID).build());
    }
}
