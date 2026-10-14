package vn.edu.cnpm.gateway.error;

public class UnknownKeyException extends InvalidTokenException {
    public UnknownKeyException(String kid) {
        super("Unknown signing key: " + kid);
    }
}
