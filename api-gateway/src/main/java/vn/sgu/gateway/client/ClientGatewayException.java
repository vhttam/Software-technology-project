package vn.sgu.gateway.client;

import org.springframework.http.HttpStatus;

public class ClientGatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final Object details;

    public ClientGatewayException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public ClientGatewayException(HttpStatus status, String errorCode, String message, Object details) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.details = details;
    }

    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
    public Object details() { return details; }
}
