package vn.edu.cnpm.gateway.filter;

import java.util.Locale;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.config.GatewayProperties.RoutePolicy;
import vn.edu.cnpm.gateway.error.AuthServiceUnavailableException;
import vn.edu.cnpm.gateway.error.GatewayErrorWriter;
import vn.edu.cnpm.gateway.error.InvalidTokenException;
import vn.edu.cnpm.gateway.security.AuthenticatedUser;
import vn.edu.cnpm.gateway.security.JwtValidator;

@Component
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {
    private final JwtValidator validator;
    private final GatewayErrorWriter errors;

    public JwtAuthenticationGlobalFilter(JwtValidator validator, GatewayErrorWriter errors) {
        this.validator = validator;
        this.errors = errors;
    }

    @Override
    public int getOrder() {
        return -100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        RoutePolicy route = exchange.getAttribute(GatewayAttributes.ROUTE_POLICY);
        if (route == null || "LOCAL".equals(route.getTarget())) return chain.filter(exchange);

        var authorizationHeaders = exchange.getRequest().getHeaders().get("Authorization");
        String authorization = authorizationHeaders == null || authorizationHeaders.size() != 1
                ? null : authorizationHeaders.get(0);
        if ("SSE_CONTENT_ADMIN".equals(route.getAuthentication())) {
            return chain.filter(exchange);
        }
        if ("PUBLIC".equals(route.getAuthentication())) {
            return chain.filter(withoutAuthorization(exchange));
        }
        String token = bearerToken(authorization);
        if (token == null) return unauthorized(exchange);

        String correlationId = exchange.getAttribute(GatewayAttributes.CORRELATION_ID);
        return validator.validate(token, correlationId)
                .flatMap(user -> authorize(exchange, chain, route, user))
                .onErrorResume(InvalidTokenException.class, exception -> unauthorized(exchange))
                .onErrorResume(AuthServiceUnavailableException.class, exception -> errors.write(exchange,
                        HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                        "Dịch vụ xác thực tạm thời không khả dụng."));
    }

    private Mono<Void> authorize(ServerWebExchange exchange, GatewayFilterChain chain,
                                 RoutePolicy route, AuthenticatedUser user) {
        if ("SUPER_ADMIN".equals(route.getAuthentication()) && !"SUPER_ADMIN".equals(user.role())) {
            return errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        }
        boolean isAdmin = "CONTENT_ADMIN".equals(user.role()) || "SUPER_ADMIN".equals(user.role());
        if ("CONTENT_ADMIN".equals(route.getAuthentication()) && !isAdmin) {
            return errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        }
        if ("SSE_CONTENT_ADMIN".equals(route.getAuthentication()) && !isAdmin) {
            return errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        }
        exchange.getAttributes().put(GatewayAttributes.USER_ID, user.userId());
        exchange.getAttributes().put(GatewayAttributes.USER_ROLE, user.role());
        ServerWebExchange authenticated = exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove("Authorization");
            headers.set("X-User-Id", user.userId());
            headers.set("X-User-Role", user.role());
            headers.set("X-Correlation-ID", exchange.getAttribute(GatewayAttributes.CORRELATION_ID));
        })).build();
        return chain.filter(authenticated);
    }

    private ServerWebExchange withoutAuthorization(ServerWebExchange exchange) {
        return exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove("Authorization");
            headers.set("X-Correlation-ID", exchange.getAttribute(GatewayAttributes.CORRELATION_ID));
        })).build();
    }

    private String bearerToken(String authorization) {
        if (authorization == null) return null;
        String[] pieces = authorization.trim().split("\\s+", 2);
        if (pieces.length != 2 || !"bearer".equals(pieces[0].toLowerCase(Locale.ROOT))
                || pieces[1].isBlank() || pieces[1].contains(" ")) return null;
        return pieces[1];
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return errors.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token không hợp lệ hoặc đã hết hạn.");
    }
}
