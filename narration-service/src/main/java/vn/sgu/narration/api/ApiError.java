package vn.sgu.narration.api;

public record ApiError(String status, String errorCode, String message, Object details, String correlationId) {
    public static ApiError of(String code, String message, Object details, String correlationId) {
        return new ApiError("error", code, message, details, correlationId);
    }
}
