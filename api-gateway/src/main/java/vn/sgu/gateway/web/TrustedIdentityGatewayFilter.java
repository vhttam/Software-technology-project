package vn.sgu.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TrustedIdentityGatewayFilter implements GlobalFilter, Ordered {
    private final String gatewayServiceToken;

    public TrustedIdentityGatewayFilter(@Value("${gateway.internal-token}") String gatewayServiceToken) {
        this.gatewayServiceToken = gatewayServiceToken;
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 20; }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
                .map(auth -> {
                    String userId = auth.getToken().getClaimAsString("userId");
                    if (userId == null || userId.isBlank()) userId = auth.getToken().getSubject();
                    String role = auth.getToken().getClaimAsString("role");
                    if (role == null || role.isBlank()) {
                        var roles = auth.getToken().getClaimAsStringList("roles");
                        role = roles == null || roles.isEmpty() ? "" : roles.get(0);
                    }
                    return withTrustedHeaders(exchange, userId, role);
                })
                .defaultIfEmpty(withTrustedHeaders(exchange, null, null))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, String userId, String role) {
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            headers.remove("X-Gateway-Service-Token");
            headers.set("X-Gateway-Service-Token", gatewayServiceToken);
            if (userId != null && !userId.isBlank()) headers.set("X-User-Id", userId);
            if (role != null && !role.isBlank()) headers.set("X-User-Role", role.replaceFirst("^ROLE_", ""));
        }).build();
        return exchange.mutate().request(request).build();
    }
}
