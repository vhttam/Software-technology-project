package vn.edu.cnpm.gateway.filter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.util.AntPathMatcher;

import vn.edu.cnpm.gateway.config.GatewayProperties;
import vn.edu.cnpm.gateway.config.GatewayProperties.RoutePolicy;
import vn.edu.cnpm.gateway.error.GatewayErrorWriter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RoutePolicyWebFilter implements WebFilter {
    private final GatewayProperties properties;
    private final GatewayErrorWriter errors;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RoutePolicyWebFilter(GatewayProperties properties, GatewayErrorWriter errors) {
        this.properties = properties;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (HttpMethod.OPTIONS.equals(request.getMethod()) && isPreflight(request)) {
            return handlePreflight(exchange);
        }

        RoutePolicy policy = findRoute(request.getMethod(), path);
        if (policy == null) {
            exchange.getAttributes().remove(GatewayAttributes.ROUTE_POLICY);
            return errors.write(exchange, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Không tìm thấy tuyến API.");
        }

        exchange.getAttributes().put(GatewayAttributes.ROUTE_POLICY, policy);
        applyCorsHeaders(exchange, request.getHeaders().getOrigin());
        if ("LOCAL".equals(policy.getTarget())) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> handlePreflight(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String requestedMethod = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        String origin = request.getHeaders().getOrigin();
        RoutePolicy policy = requestedMethod == null ? null
                : findRequestedRoute(requestedMethod, request.getPath().value());
        if (policy == null || !isAllowedOrigin(origin) || !areRequestedHeadersAllowed(request)) {
            return errors.write(exchange, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Không tìm thấy tuyến API.");
        }

        applyCorsHeaders(exchange, origin);
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                String.join(",", properties.getCors().getAllowedMethods()));
        exchange.getResponse().getHeaders().set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                String.join(",", properties.getCors().getAllowedHeaders()));
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        return exchange.getResponse().setComplete();
    }

    private boolean isPreflight(ServerHttpRequest request) {
        return request.getHeaders().containsKey(HttpHeaders.ORIGIN)
                && request.getHeaders().containsKey(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
    }

    private RoutePolicy findRoute(HttpMethod method, String path) {
        if (method == null) return null;
        return properties.getRoutes().stream()
                .filter(route -> method.name().equalsIgnoreCase(route.getMethod()))
                .filter(route -> pathMatcher.match(route.getPath(), path))
                .findFirst().orElse(null);
    }

    private RoutePolicy findRequestedRoute(String method, String path) {
        try {
            return findRoute(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), path);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isAllowedOrigin(String origin) {
        return origin != null && (properties.getCors().getAllowedOrigins().contains("*")
                || properties.getCors().getAllowedOrigins().contains(origin));
    }

    private boolean areRequestedHeadersAllowed(ServerHttpRequest request) {
        String requested = request.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        if (requested == null || requested.isBlank()) return true;
        List<String> allowed = properties.getCors().getAllowedHeaders().stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).toList();
        return Arrays.stream(requested.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .allMatch(allowed::contains);
    }

    private void applyCorsHeaders(ServerWebExchange exchange, String origin) {
        if (!isAllowedOrigin(origin)) return;
        HttpHeaders headers = exchange.getResponse().getHeaders();
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Correlation-ID");
        headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);
    }
}
