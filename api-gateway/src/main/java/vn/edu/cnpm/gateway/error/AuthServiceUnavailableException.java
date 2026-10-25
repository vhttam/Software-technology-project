package vn.edu.cnpm.gateway.error;

public class AuthServiceUnavailableException extends RuntimeException {
    public AuthServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
