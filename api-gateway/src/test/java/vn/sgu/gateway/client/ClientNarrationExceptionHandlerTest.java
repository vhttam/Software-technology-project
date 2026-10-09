package vn.sgu.gateway.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

class ClientNarrationExceptionHandlerTest {
    @Test
    void mapsClientErrorAndEchoesCorrelationId() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/client/pois/qr/narrations")
                .header("X-Correlation-ID", "corr-1").build());

        var response = new ClientNarrationExceptionHandler().handle(
                new ClientGatewayException(HttpStatus.NOT_FOUND, "NARRATION_NOT_FOUND", "Not found"), exchange);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Correlation-ID")).isEqualTo("corr-1");
        assertThat(response.getBody().errorCode()).isEqualTo("NARRATION_NOT_FOUND");
        assertThat(response.getBody().correlationId()).isEqualTo("corr-1");
    }
}
