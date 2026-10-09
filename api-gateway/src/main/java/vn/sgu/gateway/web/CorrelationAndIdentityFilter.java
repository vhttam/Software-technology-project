package vn.sgu.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationAndIdentityFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set("X-Correlation-ID", correlationId);
        var request = exchange.getRequest().mutate().headers(headers -> {
                    headers.remove("X-Correlation-ID");
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                    headers.set("X-Correlation-ID", correlationId);
                }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }
}
