package vn.edu.cnpm.gateway.error;

import org.springframework.http.HttpStatus;

public class LocalGatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Object details;

    public LocalGatewayException(HttpStatus status, String errorCode, String message, Object details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
    public Object details() { return details; }
}
