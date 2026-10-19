package vn.edu.cnpm.gateway.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import vn.edu.cnpm.gateway.filter.GatewayAttributes;

@RestControllerAdvice
public class LocalGatewayExceptionHandler {
    @ExceptionHandler(LocalGatewayException.class)
    ResponseEntity<GatewayErrorEnvelope> local(LocalGatewayException exception, ServerWebExchange exchange) {
        String correlationId = exchange.getAttribute(GatewayAttributes.CORRELATION_ID);
        return ResponseEntity.status(exception.status()).body(new GatewayErrorEnvelope("error",
                exception.errorCode(), exception.getMessage(), exception.details(), correlationId));
    }
}
