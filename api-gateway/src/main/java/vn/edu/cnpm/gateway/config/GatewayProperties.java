package vn.edu.cnpm.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {
    private String authServiceUrl;
    private String contentServiceUrl;
    private Duration jwksTtl = Duration.ofMinutes(10);
    private Duration unknownKidTtl = Duration.ofSeconds(30);
    private Duration jwtClockSkew = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration responseTimeout = Duration.ofSeconds(30);
    private Cors cors = new Cors();
    private List<RoutePolicy> routes = new ArrayList<>();

    public String getAuthServiceUrl() { return authServiceUrl; }
    public void setAuthServiceUrl(String authServiceUrl) { this.authServiceUrl = authServiceUrl; }
    public String getContentServiceUrl() { return contentServiceUrl; }
    public void setContentServiceUrl(String contentServiceUrl) { this.contentServiceUrl = contentServiceUrl; }
    public Duration getJwksTtl() { return jwksTtl; }
    public void setJwksTtl(Duration jwksTtl) { this.jwksTtl = jwksTtl; }
    public Duration getUnknownKidTtl() { return unknownKidTtl; }
    public void setUnknownKidTtl(Duration unknownKidTtl) { this.unknownKidTtl = unknownKidTtl; }
    public Duration getJwtClockSkew() { return jwtClockSkew; }
    public void setJwtClockSkew(Duration jwtClockSkew) { this.jwtClockSkew = jwtClockSkew; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
    public Cors getCors() { return cors; }
    public void setCors(Cors cors) { this.cors = cors; }
    public List<RoutePolicy> getRoutes() { return routes; }
    public void setRoutes(List<RoutePolicy> routes) { this.routes = routes; }

    public String destination(String target) {
        return switch (target) {
            case "AUTH" -> authServiceUrl;
            case "CONTENT" -> contentServiceUrl;
            case "LOCAL" -> null;
            default -> throw new IllegalArgumentException("Unsupported route target: " + target);
        };
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods = new ArrayList<>();
        private List<String> allowedHeaders = new ArrayList<>();

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public List<String> getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }
        public List<String> getAllowedHeaders() { return allowedHeaders; }
        public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }
    }

    public static class RoutePolicy {
        private String id;
        private String method;
        private String path;
        private String target;
        private String authentication;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public String getAuthentication() { return authentication; }
        public void setAuthentication(String authentication) { this.authentication = authentication; }
    }
}
