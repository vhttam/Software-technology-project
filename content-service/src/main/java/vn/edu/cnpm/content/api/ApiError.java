package vn.edu.cnpm.content.api;
public record ApiError(String status, String errorCode, String message, Object details, String correlationId) {}
