package vn.sgu.gateway.web;

import tools.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
public class GatewayErrorWriter {
    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public Mono<Void> write(ServerWebExchange exchange, int status, String code, String message) {
        try {
            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
            byte[] json = objectMapper.writeValueAsBytes(new ErrorEnvelope("error", code, message, null, correlationId));
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId == null ? "" : correlationId);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(json);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
            return exchange.getResponse().setComplete();
        }
    }

    private record ErrorEnvelope(String status, String errorCode, String message,
                                 Object details, String correlationId) {}
}
