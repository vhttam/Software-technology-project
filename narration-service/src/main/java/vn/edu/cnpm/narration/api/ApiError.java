package vn.edu.cnpm.narration.api;
public record ApiError(String status,String errorCode,String message,Object details,String correlationId){}
