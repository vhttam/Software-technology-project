package vn.edu.cnpm.translation.api;
public record ApiEnvelope<T>(String status,T data) {
    public static <T> ApiEnvelope<T> success(T data) { return new ApiEnvelope<>("success",data); }
}
