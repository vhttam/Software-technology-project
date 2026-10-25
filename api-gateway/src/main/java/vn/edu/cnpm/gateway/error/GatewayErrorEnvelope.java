package vn.edu.cnpm.gateway.error;

public record GatewayErrorEnvelope(
        String status,
        String errorCode,
        String message,
        Object details,
        String correlationId) {
}
