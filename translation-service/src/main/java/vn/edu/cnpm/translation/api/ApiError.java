package vn.edu.cnpm.translation.api;
public record ApiError(String status,String errorCode,String message,Object details,String correlationId) {}
