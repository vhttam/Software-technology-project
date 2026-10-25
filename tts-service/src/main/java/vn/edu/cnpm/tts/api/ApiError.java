package vn.edu.cnpm.tts.api;
public record ApiError(String status,String errorCode,String message,Object details,String correlationId) {}
