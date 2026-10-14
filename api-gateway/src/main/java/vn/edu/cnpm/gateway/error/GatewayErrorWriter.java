package vn.edu.cnpm.gateway.error;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.filter.GatewayAttributes;

@Component
public class GatewayErrorWriter {
    private final ObjectMapper objectMapper;

    public GatewayErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String errorCode, String message) {
        byte[] body = serialize(errorCode, message, correlationId(exchange));
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    public byte[] serialize(String errorCode, String message, String correlationId) {
        try {
            return objectMapper.writeValueAsBytes(new GatewayErrorEnvelope(
                    "error", errorCode, message, null, correlationId));
        } catch (JsonProcessingException exception) {
            String fallback = "{\"status\":\"error\",\"errorCode\":\"INTERNAL_ERROR\","
                    + "\"message\":\"Lỗi nội bộ.\",\"details\":null,\"correlationId\":\""
                    + correlationId + "\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }

    private String correlationId(ServerWebExchange exchange) {
        String id = exchange.getAttribute(GatewayAttributes.CORRELATION_ID);
        if (id != null) return id;
        String responseHeader = exchange.getResponse().getHeaders().getFirst("X-Correlation-ID");
        return responseHeader == null ? UUID.randomUUID().toString() : responseHeader;
    }
}
