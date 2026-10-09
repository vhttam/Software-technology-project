package vn.sgu.gateway.client;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class ClientNarrationExceptionHandler {
    @ExceptionHandler(ClientGatewayException.class)
    public ResponseEntity<GatewayClientError> handle(ClientGatewayException ex, ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (correlationId != null) exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);
        return ResponseEntity.status(ex.status())
                .body(new GatewayClientError("error", ex.errorCode(), ex.getMessage(), ex.details(), correlationId));
    }

    public record GatewayClientError(String status, String errorCode, String message,
                                     Object details, String correlationId) {}
}
