package vn.edu.cnpm.gateway.error;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.config.GatewayProperties.RoutePolicy;
import vn.edu.cnpm.gateway.filter.GatewayAttributes;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);
    private final GatewayErrorWriter errors;

    public GatewayExceptionHandler(GatewayErrorWriter errors) {
        this.errors = errors;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
        if (exchange.getResponse().isCommitted()) return Mono.error(exception);
        Throwable cause = deepestCause(exception);
        RoutePolicy route = exchange.getAttribute(GatewayAttributes.ROUTE_POLICY);
        if (route != null && isNetworkFailure(exception)) {
            log.warn("downstream request failed correlationId={} routeId={} causeType={}",
                    exchange.getAttribute(GatewayAttributes.CORRELATION_ID), route.getId(),
                    cause.getClass().getName());
            if ("AUTH".equals(route.getTarget())) {
                return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                        "AUTH_SERVICE_UNAVAILABLE", "Dịch vụ xác thực tạm thời không khả dụng.");
            }
            if ("CONTENT".equals(route.getTarget())) {
                return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                        "CONTENT_SERVICE_UNAVAILABLE", "Dịch vụ nội dung tạm thời không khả dụng.");
            }
            if ("NARRATION".equals(route.getTarget())) {
                return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                        "INTERNAL_ERROR", "Dịch vụ thuyết minh tạm thời không khả dụng.");
            }
        }
        if (exception instanceof NotFoundException) {
            return errors.write(exchange, HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND", "Không tìm thấy tuyến API.");
        }
        log.error("gateway request failed correlationId={} routeId={} causeType={}",
                exchange.getAttribute(GatewayAttributes.CORRELATION_ID), route == null ? "-" : route.getId(),
                cause.getClass().getName());
        return errors.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi nội bộ.");
    }

    private boolean isNetworkFailure(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ConnectException || current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException || current instanceof TimeoutException
                    || current instanceof IOException
                    || current.getClass().getSimpleName().toLowerCase().contains("timeoutexception")
                    || current.getClass().getName().toLowerCase().contains("timeout")
                    || current.getClass().getName().contains("PrematureCloseException")
                    || current.getClass().getName().contains("ConnectTimeoutException")
                    || current.getClass().getName().contains("ReadTimeoutException")) return true;
        }
        return false;
    }

    private Throwable deepestCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
}
