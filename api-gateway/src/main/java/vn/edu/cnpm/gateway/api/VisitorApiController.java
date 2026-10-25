package vn.edu.cnpm.gateway.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ConcurrentHashMap<String, Long> activeVisitors = new ConcurrentHashMap<>();

    public VisitorApiController(@Qualifier("contentInternalWebClient") WebClient content,
                                @Qualifier("narrationInternalWebClient") WebClient narration,
                                GatewayProperties properties) {
        this.content = content;
        this.narration = narration;
        this.properties = properties;
    }

    @PostMapping("/api/v1/client/presence/heartbeat")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> heartbeat(
            @RequestBody(required = false) Map<String, Object> body,
            ServerWebExchange exchange) {
        String visitorId = null;
        if (body != null && body.get("visitorId") != null) {
            visitorId = String.valueOf(body.get("visitorId"));
        } else if (exchange.getRequest().getRemoteAddress() != null) {
            visitorId = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        } else {
            visitorId = UUID.randomUUID().toString();
        }

        boolean isLeaving = body != null && (Boolean.TRUE.equals(body.get("leave")) || "true".equalsIgnoreCase(String.valueOf(body.get("leave"))));

        long now = System.currentTimeMillis();
        if (isLeaving) {
            activeVisitors.remove(visitorId);
        } else {
            activeVisitors.put(visitorId, now);
        }
        // Clean entries older than 35s
        activeVisitors.entrySet().removeIf(entry -> now - entry.getValue() > 35_000);

        int count = activeVisitors.size();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("status", "OK");
        res.put("activeVisitors", count);
        res.put("activeCount", count);
        res.put("timestamp", Instant.now().toString());
        return Mono.just(GatewaySuccessEnvelope.success(res));
    }

    @GetMapping({"/api/v1/client/presence/count", "/api/v1/presence/count"})
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> presenceCount() {
        long now = System.currentTimeMillis();
        activeVisitors.entrySet().removeIf(entry -> now - entry.getValue() > 35_000);
        int count = activeVisitors.size();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("activeVisitors", count);
        res.put("activeCount", count);
        res.put("windowSeconds", 35);
        res.put("timestamp", Instant.now().toString());
        return Mono.just(GatewaySuccessEnvelope.success(res));
    }

    @GetMapping("/api/v1/client/pois")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> listPois(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
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
                                String id = String.valueOf(row.get("poiId"));
                                String name = row.get("name") != null ? String.valueOf(row.get("name")) : "";
                                String desc = row.get("description") != null ? String.valueOf(row.get("description")) : "";
                                item.put("poiId", id);
                                item.put("poiName", name);
                                item.put("description", desc);
                                item.put("qrCode", row.get("qrCode"));

                                // Attach coordinates (real or deterministic based on name/id)
                                double[] coords = resolveCoordinates(name, row.get("latitude"), row.get("longitude"));
                                item.put("latitude", coords[0]);
                                item.put("longitude", coords[1]);
                                item.put("triggerRadius", row.get("triggerRadius") != null ? row.get("triggerRadius") : 50);

                                items.add(item);
                            }
                        }
                    }
                    result.put("items", items);
                    return GatewaySuccessEnvelope.success(result);
                });
    }

    @GetMapping("/api/v1/client/pois/nearby")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> nearbyPois(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(defaultValue = "5000") double radius,
            ServerWebExchange exchange) {
        return listPois(1, 100, null, exchange).map(envelope -> {
            Map<String, Object> data = envelope.data();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

            double userLat = lat != null ? lat : 10.7720;
            double userLng = lng != null ? lng : 106.7050;

            List<Map<String, Object>> nearbyList = new ArrayList<>();
            for (Map<String, Object> item : items) {
                Map<String, Object> copy = new LinkedHashMap<>(item);
                double pLat = ((Number) copy.get("latitude")).doubleValue();
                double pLng = ((Number) copy.get("longitude")).doubleValue();

                double distance = calculateHaversine(userLat, userLng, pLat, pLng);
                copy.put("distanceMeters", Math.round(distance));
                if (distance < 1000) {
                    copy.put("distanceText", Math.round(distance) + "m");
                } else {
                    copy.put("distanceText", String.format(Locale.US, "%.1fkm", distance / 1000.0));
                }
                nearbyList.add(copy);
            }

            nearbyList.sort(Comparator.comparingDouble(a -> ((Number) a.get("distanceMeters")).doubleValue()));

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("items", nearbyList);
            res.put("userLat", userLat);
            res.put("userLng", userLng);
            res.put("total", nearbyList.size());
            return GatewaySuccessEnvelope.success(res);
        });
    }

    @GetMapping("/api/v1/client/qr/{qrCode}")
    public Mono<GatewaySuccessEnvelope<Map<String, Object>>> qr(@PathVariable String qrCode,
                                                                 ServerWebExchange exchange) {
        return getData(content, "/internal/pois/by-qr/" + qrCode, Map.of(), null, exchange, "CONTENT")
                .map(data -> {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.put("poiId", data.get("poiId"));
                    return GatewaySuccessEnvelope.success(res);
                });
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

    private double[] resolveCoordinates(String name, Object latObj, Object lngObj) {
        if (latObj instanceof Number lat && lngObj instanceof Number lng) {
            return new double[]{lat.doubleValue(), lng.doubleValue()};
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("bạch đằng") || lower.contains("bach dang")) {
            return new double[]{10.7719, 106.7058};
        } else if (lower.contains("nguyễn huệ") || lower.contains("nguyen hue")) {
            return new double[]{10.7745, 106.7032};
        } else if (lower.contains("lăng bác") || lower.contains("lang bac")) {
            return new double[]{21.0368, 105.8346};
        } else if (lower.contains("chợ bến thành") || lower.contains("ben thanh")) {
            return new double[]{10.7725, 106.6980};
        } else if (lower.contains("dinh độc lập") || lower.contains("dinh doc lap")) {
            return new double[]{10.7770, 106.6953};
        } else if (lower.contains("nhà thờ đức bà") || lower.contains("duc ba")) {
            return new double[]{10.7798, 106.6990};
        }
        return new double[]{10.7730, 106.7040};
    }

    private double calculateHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
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
            if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
            }
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
