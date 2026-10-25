package vn.edu.cnpm.gateway.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.config.GatewayProperties.RoutePolicy;
import vn.edu.cnpm.gateway.error.GatewayErrorWriter;

@Component
public class DownstreamStatusMappingGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter errors;

    public DownstreamStatusMappingGlobalFilter(GatewayErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public int getOrder() {
        return NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse original = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                HttpStatusCode status = getStatusCode();
                if (status == null || !List.of(HttpStatus.BAD_GATEWAY.value(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                        HttpStatus.GATEWAY_TIMEOUT.value()).contains(status.value())) {
                    return super.writeWith(body);
                }

                RoutePolicy route = exchange.getAttribute(GatewayAttributes.ROUTE_POLICY);
                String target = route == null ? null : route.getTarget();
                String code = switch (target == null ? "" : target) {
                    case "AUTH" -> "AUTH_SERVICE_UNAVAILABLE";
                    case "CONTENT" -> "CONTENT_SERVICE_UNAVAILABLE";
                    default -> "INTERNAL_ERROR";
                };
                String message = switch (target == null ? "" : target) {
                    case "AUTH" -> "Dịch vụ xác thực tạm thời không khả dụng.";
                    case "CONTENT" -> "Dịch vụ nội dung tạm thời không khả dụng.";
                    default -> "Dịch vụ thuyết minh tạm thời không khả dụng.";
                };
                Mono<Void> release = DataBufferUtils.join(Flux.from(body))
                        .doOnNext(DataBufferUtils::release).then();
                return release.then(Mono.defer(() -> {
                    getDelegate().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    HttpHeaders headers = getDelegate().getHeaders();
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                    headers.remove(HttpHeaders.TRANSFER_ENCODING);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    byte[] bytes = errors.serialize(code, message,
                            exchange.getResponse().getHeaders().getFirst("X-Correlation-ID"));
                    return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                }));
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMapSequential(Flux::from));
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }
}
