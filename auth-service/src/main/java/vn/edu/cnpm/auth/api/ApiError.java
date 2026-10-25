package vn.edu.cnpm.auth.api;
public record ApiError(String status, String errorCode, String message, Object details, String correlationId) {}
