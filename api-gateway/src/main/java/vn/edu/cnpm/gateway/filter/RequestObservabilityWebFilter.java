package vn.edu.cnpm.gateway.filter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestObservabilityWebFilter implements WebFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityWebFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        exchange.getAttributes().put(GatewayAttributes.CORRELATION_ID, correlationId);
        exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);
            return Mono.empty();
        });
        long started = System.nanoTime();

        return chain.filter(exchange).contextWrite(context -> context.put(GatewayAttributes.CORRELATION_ID, correlationId))
                .doFinally(signal -> {
            Object policy = exchange.getAttribute(GatewayAttributes.ROUTE_POLICY);
            String routeId = policy instanceof vn.edu.cnpm.gateway.config.GatewayProperties.RoutePolicy route
                    ? route.getId() : "unmatched";
            Object userId = exchange.getAttribute(GatewayAttributes.USER_ID);
            int status = exchange.getResponse().getStatusCode() == null ? 200
                    : exchange.getResponse().getStatusCode().value();
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("request correlationId={} method={} path={} routeId={} status={} durationMs={} userId={}",
                    correlationId, exchange.getRequest().getMethod(), safePath(exchange), routeId, status,
                    elapsed, userId == null ? "-" : userId);
                });
    }

    private String safePath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) return path;
        String safeQuery = Arrays.stream(rawQuery.split("&"))
                .filter(part -> !"accesstoken".equalsIgnoreCase(decodeKey(part)))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return safeQuery.isEmpty() ? path : path + "?" + safeQuery;
    }

    private String decodeKey(String pair) {
        String key = pair.split("=", 2)[0];
        try {
            return URLDecoder.decode(key, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return key;
        }
    }
}
