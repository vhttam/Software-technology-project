package vn.sgu.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CorrelationAndIdentityFilterTest {
    @Test
    void replacesClientCorrelationAndRemovesClientIdentityHeaders() {
        CorrelationAndIdentityFilter filter = new CorrelationAndIdentityFilter();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .header("X-Correlation-ID", "spoofed-correlation")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Role", "ADMIN")
                .build());
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        String correlationId = forwarded.get().getRequest().getHeaders().getFirst("X-Correlation-ID");
        assertThat(correlationId).isNotBlank().isNotEqualTo("spoofed-correlation");
        assertThat(forwarded.get().getResponse().getHeaders().getFirst("X-Correlation-ID")).isEqualTo(correlationId);
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Role")).isNull();
    }
}
