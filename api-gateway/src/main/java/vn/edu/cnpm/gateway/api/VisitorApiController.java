package vn.edu.cnpm.gateway.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import vn.edu.cnpm.gateway.config.GatewayProperties;
import vn.edu.cnpm.gateway.error.LocalGatewayException;
import vn.edu.cnpm.gateway.filter.GatewayAttributes;

@RestController
public class VisitorApiController {
    private final WebClient content;
    private final WebClient narration;
    private final GatewayProperties properties;

    public VisitorApiController(@Qualifier("contentInternalWebClient") WebClient content,
                                @Qualifier("narrationInternalWebClient") WebClient narration,
                                GatewayProperties properties) {
        this.content = content;
        this.narration = narration;
        this.properties = properties;
    }

    @GetMapping("/api/v1/client/pois")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> listPois(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        return getData(content, "/internal/pois", Map.of("page", page, "pageSize", pageSize), query,
                exchange, "CONTENT").map(data -> {
                    Map<String, Object> result = new LinkedHashMap<>(data);
                    Object rawItems = data.get("items");
                    List<Map<String, Object>> items = new ArrayList<>();
                    if (rawItems instanceof List<?> rows) {
                        for (Object raw : rows) {
                            if (raw instanceof Map<?, ?> row) {
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("poiId", row.get("poiId"));
                                item.put("poiName", row.get("name"));
                                item.put("description", row.get("description"));
                                items.add(item);
                            }
                        }
                    }
                    result.put("items", items);
                    return GatewaySuccessEnvelope.success(result);
                });
    }

    @GetMapping("/api/v1/client/qr/{qrCode}")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> qr(@PathVariable String qrCode,
                                                                 ServerWebExchange exchange) {
        return getData(content, "/internal/pois/by-qr/" + qrCode, Map.of(), null, exchange, "CONTENT")
                .map(data -> GatewaySuccessEnvelope.success(Map.of("poiId", data.get("poiId"))));
    }

    @GetMapping("/api/v1/client/pois/{poiId}/narrations")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> listNarrations(@PathVariable String poiId,
                                                                             ServerWebExchange exchange) {
        return currentPoi(poiId, exchange).flatMap(poi -> getData(narration, "/internal/narrations",
                Map.of("contentId", String.valueOf(poi.get("contentId"))), null, exchange, "NARRATION")
                .map(data -> {
                    Map<String, Object> result = poiMetadata(poi);
                    result.put("narrations", data.getOrDefault("narrations", List.of()));
                    return GatewaySuccessEnvelope.success(result);
                }));
    }

    @GetMapping("/api/v1/client/pois/{poiId}/narrations/{lang}")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> getNarration(@PathVariable String poiId,
                                                                           @PathVariable String lang,
                                                                           ServerWebExchange exchange) {
        return currentPoi(poiId, exchange).flatMap(poi -> getData(narration, "/internal/narrations/" + lang,
                Map.of("contentId", String.valueOf(poi.get("contentId"))), null, exchange, "NARRATION")
                .map(data -> {
                    Map<String, Object> result = poiMetadata(poi);
                    result.putAll(data);
                    return GatewaySuccessEnvelope.success(result);
                }));
    }

    private Mono<Map<String, Object>> currentPoi(String poiId, ServerWebExchange exchange) {
        return getData(content, "/internal/pois/" + poiId + "/current", Map.of(), null, exchange, "CONTENT");
    }

    private Map<String, Object> poiMetadata(Map<String, Object> poi) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("poiId", poi.get("poiId"));
        result.put("poiName", poi.get("poiName"));
        result.put("contentTitle", poi.get("contentTitle"));
        return result;
    }

    private Mono<Map<String, Object>> getData(WebClient client, String path, Map<String, ?> params,
                                               String query, ServerWebExchange exchange, String dependency) {
        return client.get().uri(builder -> {
                    var uri = builder.path(path);
                    params.forEach(uri::queryParam);
                    if (query != null && !query.isBlank()) uri.queryParam("query", query);
                    return uri.build();
                })
                .header("X-Service-Token", properties.getServiceToken())
                .header("X-Correlation-ID", correlationId(exchange))
                .exchangeToMono(response -> decode(response, dependency))
                .onErrorMap(exception -> exception instanceof LocalGatewayException ? exception
                        : unavailable(dependency));
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> decode(ClientResponse response, String dependency) {
        return response.bodyToMono(Map.class).defaultIfEmpty(Map.of()).flatMap(envelope -> {
            if (response.statusCode().is2xxSuccessful() && envelope.get("data") instanceof Map<?, ?> data) {
                return Mono.just((Map<String, Object>) data);
            }
            HttpStatus status = HttpStatus.resolve(response.statusCode().value());
<<<<<<< HEAD
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
=======
>>>>>>> origin/main
            Object errorCode = envelope.get("errorCode");
            Object errorMessage = envelope.get("message");
            String code = errorCode == null ? "INTERNAL_ERROR" : String.valueOf(errorCode);
            String message = errorMessage == null ? "Dịch vụ nội bộ tạm thời không khả dụng" : String.valueOf(errorMessage);
            Object details = envelope.get("details");
            return Mono.error(new LocalGatewayException(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                    code, message, details));
        });
    }

    private String correlationId(ServerWebExchange exchange) {
        String id = exchange.getAttribute(GatewayAttributes.CORRELATION_ID);
        return id == null ? "" : id;
    }

    private LocalGatewayException unavailable(String dependency) {
        String code = "CONTENT".equals(dependency) ? "CONTENT_SERVICE_UNAVAILABLE" : "INTERNAL_ERROR";
        return new LocalGatewayException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "Dịch vụ " + ("CONTENT".equals(dependency) ? "nội dung" : "thuyết minh") + " tạm thời không khả dụng.", null);
    }
}
