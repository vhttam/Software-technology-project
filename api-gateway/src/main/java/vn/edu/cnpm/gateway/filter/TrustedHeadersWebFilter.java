package vn.edu.cnpm.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TrustedHeadersWebFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerWebExchange sanitized = exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove("X-User-Id");
            headers.remove("X-User-Role");
            if (!"/internal/callbacks/job-progress".equals(exchange.getRequest().getPath().value())) {
                headers.remove("X-Service-Token");
            }
            headers.remove("X-Correlation-ID");
        })).build();
        return chain.filter(sanitized);
    }
}
