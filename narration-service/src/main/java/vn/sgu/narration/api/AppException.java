package vn.sgu.narration.api;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Object details;

    public AppException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public AppException(HttpStatus status, String errorCode, String message, Object details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
    public Object details() { return details; }
}
