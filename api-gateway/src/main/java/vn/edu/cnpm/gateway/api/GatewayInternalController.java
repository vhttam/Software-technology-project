package vn.edu.cnpm.gateway.api;

import java.security.MessageDigest;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import vn.edu.cnpm.gateway.error.GatewayErrorWriter;
import vn.edu.cnpm.gateway.error.AuthServiceUnavailableException;
import vn.edu.cnpm.gateway.filter.GatewayAttributes;
import vn.edu.cnpm.gateway.error.InvalidTokenException;
import vn.edu.cnpm.gateway.security.AuthenticatedUser;
import vn.edu.cnpm.gateway.security.JwtValidator;
import vn.edu.cnpm.gateway.sse.JobProgressStreamRegistry;

@RestController
public class GatewayInternalController {
    private final JwtValidator jwtValidator;
    private final GatewayErrorWriter errors;
    private final JobProgressStreamRegistry streams;
    private final ObjectMapper mapper;
    private final String narrationToken;

    public GatewayInternalController(JwtValidator jwtValidator, GatewayErrorWriter errors,
                                      JobProgressStreamRegistry streams, ObjectMapper mapper,
                                      Environment environment) {
        this.jwtValidator = jwtValidator;
        this.errors = errors;
        this.streams = streams;
        this.mapper = mapper;
        this.narrationToken = environment.getProperty("NARRATION_SERVICE_TOKEN", "");
    }

    @PostMapping("/internal/callbacks/job-progress")
    public Mono<Void> callback(@RequestHeader(value = "X-Service-Token", required = false) String token,
                               @RequestBody Map<String, Object> body,
                               ServerWebExchange exchange) {
        if (!matches(narrationToken, token)) {
            return errors.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu hoặc sai X-Service-Token.");
        }
        Object event = body.get("event");
        Object data = body.get("data");
        if (!"job.progress".equals(event) || !(data instanceof Map<?, ?> progress)
                || progress.get("jobId") == null || String.valueOf(progress.get("jobId")).isBlank()) {
            return errors.write(exchange, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Callback tiến độ không hợp lệ.");
        }
        streams.publish(String.valueOf(progress.get("jobId")), data);
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        return exchange.getResponse().setComplete();
    }

    @GetMapping(value = "/api/v1/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<Void> events(@PathVariable String jobId,
                             @org.springframework.web.bind.annotation.RequestParam(required = false) String accessToken,
                             ServerWebExchange exchange) {
        String correlationId = correlationId(exchange);
        if (accessToken == null || accessToken.isBlank()) {
            return errors.write(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Access token không hợp lệ hoặc đã hết hạn.");
        }
        return jwtValidator.validate(accessToken, correlationId)
                .flatMap(user -> writeAuthorizedStream(user, jobId, exchange))
                .onErrorResume(InvalidTokenException.class, exception -> errors.write(exchange, HttpStatus.UNAUTHORIZED,
                        "UNAUTHORIZED", "Access token không hợp lệ hoặc đã hết hạn."))
                .onErrorResume(AuthServiceUnavailableException.class, exception -> errors.write(exchange,
                        HttpStatus.SERVICE_UNAVAILABLE, "AUTH_SERVICE_UNAVAILABLE",
                        "Dịch vụ xác thực tạm thời không khả dụng."));
    }

    private Mono<Void> writeAuthorizedStream(AuthenticatedUser user, String jobId, ServerWebExchange exchange) {
        if (!"CONTENT_ADMIN".equals(user.role()) && !"SUPER_ADMIN".equals(user.role())) {
            return errors.write(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        }
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
        exchange.getResponse().getHeaders().setCacheControl("no-cache");
        Flux<byte[]> frames = streams.subscribe(jobId).map(this::encodeEvent);
        return exchange.getResponse().writeAndFlushWith(frames.map(bytes ->
                Mono.just(exchange.getResponse().bufferFactory().wrap(bytes))));
    }

    private byte[] encodeEvent(Object eventData) {
        try {
            String json = mapper.writeValueAsString(eventData);
            return ("event: job.progress\ndata: " + json + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (JsonProcessingException exception) {
            return "event: job.progress\ndata: {}\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private boolean matches(String expected, String actual) {
        return expected != null && !expected.isBlank() && actual != null
                && MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String correlationId(ServerWebExchange exchange) {
        String id = exchange.getAttribute(GatewayAttributes.CORRELATION_ID);
        return id == null ? "" : id;
    }
}
