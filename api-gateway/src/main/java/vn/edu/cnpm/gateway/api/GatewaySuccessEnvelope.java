package vn.edu.cnpm.gateway.api;

public record GatewaySuccessEnvelope<T>(String status, T data) {
    public static <T> GatewaySuccessEnvelope<T> success(T data) {
        return new GatewaySuccessEnvelope<>("success", data);
    }
}
