package vn.sgu.gateway.client;

import org.junit.jupiter.api.Test;
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
    void browsesPoisWithPaginationAndDoesNotExposeQrCode() {
        List<org.springframework.web.reactive.function.client.ClientRequest> requests = new CopyOnWriteArrayList<>();
        ExchangeFunction exchange = request -> {
            requests.add(request);
            return json(HttpStatus.OK, """
                    {"status":"success","data":{"items":[{"poiId":"poi-1","poiName":"Museum","description":"History","qrCode":"must-not-leak"}],"page":2,"pageSize":5,"totalItems":11,"totalPages":3}}
                    """);
        };
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.browsePois(2, 5, "museum", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.data().items()).containsExactly(
                new ClientNarrationGatewayController.PoiSummary("poi-1", "Museum", "History"));
        assertThat(result.data().page()).isEqualTo(2);
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).url().getPath()).isEqualTo("/internal/pois");
        assertThat(requests.get(0).url().getQuery()).contains("page=2", "pageSize=5", "query=museum");
        assertThat(requests.get(0).headers().getFirst("X-Service-Token")).isEqualTo("gateway-secret");
        assertThat(requests.get(0).headers().getFirst("X-Correlation-ID")).isEqualTo(CORRELATION_ID);
    }

    @Test
    void qrResolutionReturnsOnlyPoiIdAndUsesUnifiedServiceHeader() {
        List<org.springframework.web.reactive.function.client.ClientRequest> requests = new CopyOnWriteArrayList<>();
        ExchangeFunction exchange = request -> {
            requests.add(request);
            return poiResponse("poi-1", "content-1", "Museum");
        };
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.resolveQr("qr-1", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.data()).isEqualTo(new ClientNarrationGatewayController.ResolvedPoi("poi-1"));
        assertThat(requests.get(0).url().getPath()).isEqualTo("/internal/pois/by-qr/qr-1");
        assertThat(requests.get(0).headers().getFirst("X-Service-Token")).isEqualTo("gateway-secret");
    }

    @Test
    void listAggregatesPoiAndPublishedLanguagesUsingPoiId() {
        List<org.springframework.web.reactive.function.client.ClientRequest> requests = new CopyOnWriteArrayList<>();
        ExchangeFunction exchange = request -> {
            requests.add(request);
            if (request.url().getPath().contains("/internal/pois/")) {
                return poiResponse("poi-1", "content-1", "Museum");
            }
            return json(HttpStatus.OK, """
                    {"status":"success","data":{"narrations":[{"lang":"en","version":4},{"lang":"vi","version":3}]}}
                    """);
        };
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.list("poi-1", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.data().poiId()).isEqualTo("poi-1");
        assertThat(result.data().contentTitle()).isEqualTo("History");
        assertThat(result.data().narrations()).containsExactly(
                new ClientNarrationGatewayController.LanguageView("en", 4),
                new ClientNarrationGatewayController.LanguageView("vi", 3));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).url().getPath()).isEqualTo("/internal/pois/poi-1/current");
        assertThat(requests.get(1).headers().getFirst("X-Service-Token")).isEqualTo("gateway-secret");
        assertThat(requests).allSatisfy(request ->
                assertThat(request.headers().getFirst("X-Correlation-ID")).isEqualTo(CORRELATION_ID));
    }

    @Test
    void getAggregatesPoiAndNarrationArtifact() {
        ExchangeFunction exchange = request -> request.url().getPath().contains("/internal/pois/")
                ? poiResponse("poi-2", "content-2", "Garden")
                : json(HttpStatus.OK, """
                    {"status":"success","data":{"lang":"fr","version":7,"subtitle":"Bonjour","audioUrl":"https://audio.example/signed","audioUrlExpiresAt":"2026-10-09T12:00:00Z"}}
                    """);
        ClientNarrationGatewayController controller = controller(exchange, false);

        var result = controller.get("poi-2", "fr", exchangeWithCorrelation()).block(Duration.ofSeconds(2));

        assertThat(result.data().poiId()).isEqualTo("poi-2");
        assertThat(result.data().lang()).isEqualTo("fr");
        assertThat(result.data().version()).isEqualTo(7);
        assertThat(result.data().subtitle()).isEqualTo("Bonjour");
        assertThat(result.data().audioUrl()).isEqualTo("https://audio.example/signed");
    }

    @Test
    void missingNarrationPreservesAvailableLanguagesDetails() {
        ExchangeFunction exchange = request -> request.url().getPath().contains("/internal/pois/")
                ? poiResponse("poi-1", "content-1", "Museum")
                : json(HttpStatus.NOT_FOUND, """
                    {"status":"error","errorCode":"NARRATION_NOT_FOUND","message":"No narration","details":{"availableLangs":["en","vi"]},"correlationId":"corr-123"}
                    """);
        ClientNarrationGatewayController controller = controller(exchange, false);

        assertThatThrownBy(() -> controller.get("poi-1", "fr", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("NARRATION_NOT_FOUND");
                    assertThat(ex.details()).isEqualTo(java.util.Map.of("availableLangs", List.of("en", "vi")));
                });
    }

    @Test
    void mockModeReturnsSampleAndMapsMissingPoi() {
        ClientNarrationGatewayController controller = controller(request -> json(HttpStatus.OK,
                "{\"status\":\"success\",\"data\":{\"narrations\":[]}}"), true);
        var page = controller.browsePois(1, 20, null, exchangeWithCorrelation()).block(Duration.ofSeconds(1));
        assertThat(page.data().items()).containsExactly(new ClientNarrationGatewayController.PoiSummary(
                "poi-demo", "Điểm tham quan mẫu", "Mô tả điểm tham quan mẫu"));
        assertThat(controller.resolveQr("qr-sample", exchangeWithCorrelation()).block().data().poiId())
                .isEqualTo("poi-demo");

        assertThatThrownBy(() -> controller.list("missing-poi", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("POI_NOT_FOUND");
                });
    }

    @Test
    void downstreamNotFoundAndServerFailureAreMapped() {
        ClientNarrationGatewayController notFoundController = controller(
                request -> json(HttpStatus.NOT_FOUND, "{}"), false);
        assertThatThrownBy(() -> notFoundController.resolveQr("qr-1", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.errorCode()).isEqualTo("POI_NOT_FOUND");
                });

        ClientNarrationGatewayController unavailableController = controller(
                request -> json(HttpStatus.INTERNAL_SERVER_ERROR, "{}"), false);
        assertThatThrownBy(() -> unavailableController.resolveQr("qr-1", exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.errorCode()).isEqualTo("CONTENT_SERVICE_UNAVAILABLE");
                });
    }

    @Test
    void pageSizeAboveMaximumIsRejected() {
        ClientNarrationGatewayController controller = controller(request -> json(HttpStatus.OK, "{}"), true);
        assertThatThrownBy(() -> controller.browsePois(1, 101, null, exchangeWithCorrelation()).block())
                .isInstanceOfSatisfying(ClientGatewayException.class, ex ->
                        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static ClientNarrationGatewayController controller(ExchangeFunction exchange, boolean mocksEnabled) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchange);
        return new ClientNarrationGatewayController(builder, "http://content.test", "http://narration.test",
                "gateway-secret", mocksEnabled);
    }

    private static Mono<ClientResponse> poiResponse(String poiId, String contentId, String poiName) {
        return json(HttpStatus.OK, """
                {"status":"success","data":{"poiId":"%s","poiName":"%s","description":"History","contentId":"%s","contentTitle":"History","isDeleted":false}}
                """.formatted(poiId, poiName, contentId));
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
