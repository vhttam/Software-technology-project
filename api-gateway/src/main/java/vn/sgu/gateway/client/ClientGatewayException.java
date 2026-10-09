package vn.sgu.gateway.client;

import org.springframework.http.HttpStatus;

public class ClientGatewayException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public ClientGatewayException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
}
