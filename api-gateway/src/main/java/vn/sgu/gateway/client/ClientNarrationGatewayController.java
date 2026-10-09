package vn.sgu.gateway.client;

import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/v1/client")
public class ClientNarrationGatewayController {
    private static final ParameterizedTypeReference<SuccessEnvelope<PoiSnapshot>> POI_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SuccessEnvelope<PoiPage>> POI_PAGE_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SuccessEnvelope<PublishedNarrations>> LIST_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SuccessEnvelope<NarrationArtifact>> NARRATION_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final WebClient content;
    private final WebClient narration;
    private final String serviceToken;
    private final boolean mocksEnabled;

    public ClientNarrationGatewayController(WebClient.Builder builder,
            @Value("${gateway.services.content-url}") String contentUrl,
            @Value("${gateway.services.narration-url}") String narrationUrl,
            @Value("${gateway.service-token}") String serviceToken,
            @Value("${gateway.client.mocks.enabled:true}") boolean mocksEnabled) {
        this.content = builder.baseUrl(contentUrl).build();
        this.narration = builder.baseUrl(narrationUrl).build();
        this.serviceToken = serviceToken;
        this.mocksEnabled = mocksEnabled;
    }

    @GetMapping("/pois")
    public Mono<SuccessEnvelope<PoiPage>> browsePois(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) int pageSize,
            @RequestParam(required = false) String query,
            ServerWebExchange exchange) {
        if (pageSize > 100) {
            return Mono.error(new ClientGatewayException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "pageSize không được vượt quá 100"));
        }
        if (mocksEnabled) return Mono.just(SuccessEnvelope.success(mockPoiPage(page, pageSize, query)));

        String correlationId = correlationId(exchange);
        return unwrap(content.get().uri(uriBuilder -> uriBuilder.path("/internal/pois")
                                .queryParam("page", page).queryParam("pageSize", pageSize)
                                .queryParamIfPresent("query", java.util.Optional.ofNullable(query))
                                .build())
                        .header("X-Service-Token", serviceToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(POI_PAGE_RESPONSE),
                "CONTENT_SERVICE_UNAVAILABLE", "CONTENT_SERVICE_UNAVAILABLE", "Content Service tạm thời không khả dụng")
                .map(SuccessEnvelope::success);
    }

    @GetMapping("/qr/{qrCode}")
    public Mono<SuccessEnvelope<ResolvedPoi>> resolveQr(@PathVariable String qrCode,
                                                         ServerWebExchange exchange) {
        return activePoiByQr(qrCode, correlationId(exchange))
                .map(poi -> SuccessEnvelope.success(new ResolvedPoi(poi.poiId())));
    }

    @GetMapping("/pois/{poiId}/narrations")
    public Mono<SuccessEnvelope<NarrationList>> list(@PathVariable String poiId,
                                                     ServerWebExchange exchange) {
        String correlationId = correlationId(exchange);
        return activePoiById(poiId, correlationId).flatMap(poi ->
                publishedNarrations(poi.contentId(), correlationId)
                        .map(published -> SuccessEnvelope.success(new NarrationList(
                                poi.poiId(), poi.poiName(), poi.contentTitle(), published.narrations()))));
    }

    @GetMapping("/pois/{poiId}/narrations/{lang}")
    public Mono<SuccessEnvelope<ClientNarration>> get(@PathVariable String poiId, @PathVariable String lang,
                                                       ServerWebExchange exchange) {
        String correlationId = correlationId(exchange);
        return activePoiById(poiId, correlationId).flatMap(poi ->
                narrationFor(poi.contentId(), lang, correlationId)
                        .map(artifact -> SuccessEnvelope.success(new ClientNarration(
                                poi.poiId(), poi.poiName(), poi.contentTitle(), artifact.lang(), artifact.version(),
                                artifact.subtitle(), artifact.audioUrl(), artifact.audioUrlExpiresAt()))));
    }

    private Mono<PoiSnapshot> activePoiByQr(String qrCode, String correlationId) {
        if (mocksEnabled) {
            if (qrCode.startsWith("missing-")) return poiNotFound();
            return Mono.just(mockPoi());
        }
        return activePoi(content.get().uri("/internal/pois/by-qr/{qrCode}", qrCode)
                        .header("X-Service-Token", serviceToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(POI_RESPONSE), correlationId);
    }

    private Mono<PoiSnapshot> activePoiById(String poiId, String correlationId) {
        if (mocksEnabled) {
            if (poiId.startsWith("missing-")) return poiNotFound();
            return Mono.just(mockPoi());
        }
        return activePoi(content.get().uri("/internal/pois/{poiId}/current", poiId)
                        .header("X-Service-Token", serviceToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(POI_RESPONSE), correlationId);
    }

    private Mono<PoiSnapshot> activePoi(Mono<SuccessEnvelope<PoiSnapshot>> response, String correlationId) {
        return unwrap(response, "CONTENT_SERVICE_UNAVAILABLE", "POI_NOT_FOUND",
                "Điểm tham quan hoặc nội dung không tồn tại")
                .flatMap(poi -> poi.contentId() == null || poi.contentId().isBlank()
                        || poi.poiId() == null || poi.poiId().isBlank()
                        || poi.isDeleted() != null && poi.isDeleted()
                        ? poiNotFound() : Mono.just(poi));
    }

    private Mono<PublishedNarrations> publishedNarrations(String contentId, String correlationId) {
        return unwrap(narration.get().uri(uriBuilder -> uriBuilder.path("/internal/narrations")
                                .queryParam("contentId", contentId).build())
                        .header("X-Service-Token", serviceToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(LIST_RESPONSE),
                "NARRATION_SERVICE_UNAVAILABLE", "NARRATION_NOT_FOUND", "Chưa có thuyết minh PUBLISHED");
    }

    private Mono<NarrationArtifact> narrationFor(String contentId, String lang, String correlationId) {
        return unwrap(narration.get().uri(uriBuilder -> uriBuilder.path("/internal/narrations/{lang}")
                                .queryParam("contentId", contentId).build(lang))
                        .header("X-Service-Token", serviceToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(NARRATION_RESPONSE),
                "NARRATION_SERVICE_UNAVAILABLE", "NARRATION_NOT_FOUND",
                "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn");
    }

    private <T> Mono<T> unwrap(Mono<SuccessEnvelope<T>> response, String unavailableCode,
                               String notFoundCode, String notFoundMessage) {
        return response.flatMap(envelope -> envelope == null || envelope.data() == null
                        ? Mono.error(new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc trả dữ liệu không hợp lệ"))
                        : Mono.just(envelope.data()))
                .onErrorMap(WebClientResponseException.NotFound.class,
                        ex -> downstreamNotFound(ex, notFoundCode, notFoundMessage))
                .onErrorMap(WebClientResponseException.class,
                        ex -> new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc tạm thời không khả dụng"))
                .onErrorMap(WebClientRequestException.class,
                        ex -> new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc tạm thời không khả dụng"));
    }

    private ClientGatewayException downstreamNotFound(WebClientResponseException exception,
                                                        String fallbackCode, String fallbackMessage) {
        ErrorPayload payload = null;
        try {
            payload = exception.getResponseBodyAs(ErrorPayload.class);
        } catch (Exception ignored) {
            // The fallback below keeps the public error envelope stable when a dependency has no JSON body.
        }
        String code = payload != null && "NARRATION_NOT_FOUND".equals(payload.errorCode())
                ? payload.errorCode() : fallbackCode;
        Object details = payload == null ? null : payload.details();
        if ("NARRATION_NOT_FOUND".equals(code) && details == null) {
            details = Map.of("availableLangs", List.of());
        }
        String message = payload == null || payload.message() == null ? fallbackMessage : payload.message();
        return new ClientGatewayException(HttpStatus.NOT_FOUND, code, message, details);
    }

    private static String correlationId(ServerWebExchange exchange) {
        return exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
    }

    private static Mono<PoiSnapshot> poiNotFound() {
        return Mono.error(new ClientGatewayException(HttpStatus.NOT_FOUND,
                "POI_NOT_FOUND", "Điểm tham quan hoặc nội dung không tồn tại"));
    }

    private static PoiSnapshot mockPoi() {
        return new PoiSnapshot("poi-demo", "Điểm tham quan mẫu", "Mô tả điểm tham quan mẫu",
                "content-demo", "Thuyết minh mẫu", false);
    }

    private static PoiPage mockPoiPage(int page, int pageSize, String query) {
        List<PoiSummary> filtered = List.of(new PoiSummary("poi-demo", "Điểm tham quan mẫu",
                        "Mô tả điểm tham quan mẫu")).stream()
                .filter(poi -> query == null || query.isBlank()
                        || poi.poiName().toLowerCase(java.util.Locale.ROOT)
                        .contains(query.toLowerCase(java.util.Locale.ROOT)))
                .toList();
        int from = Math.min((page - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        long totalPages = filtered.isEmpty() ? 0 : (filtered.size() + (long) pageSize - 1) / pageSize;
        return new PoiPage(filtered.subList(from, to), page, pageSize, filtered.size(), totalPages);
    }

    public record SuccessEnvelope<T>(String status, T data) {
        static <T> SuccessEnvelope<T> success(T data) { return new SuccessEnvelope<>("success", data); }
    }
    public record ErrorPayload(String status, String errorCode, String message, Map<String, Object> details,
                               String correlationId) {}
    public record PoiSnapshot(String poiId, String poiName, String description, String contentId,
                              String contentTitle, Boolean isDeleted) {}
    public record PoiSummary(String poiId, String poiName, String description) {}
    public record PoiPage(List<PoiSummary> items, int page, int pageSize, long totalItems, long totalPages) {}
    public record ResolvedPoi(String poiId) {}
    public record LanguageView(String lang, int version) {}
    public record PublishedNarrations(List<LanguageView> narrations) {}
    public record NarrationArtifact(String lang, int version, String subtitle, String audioUrl,
                                    Instant audioUrlExpiresAt) {}
    public record NarrationList(String poiId, String poiName, String contentTitle, List<LanguageView> narrations) {}
    public record ClientNarration(String poiId, String poiName, String contentTitle, String lang, int version,
                                  String subtitle, String audioUrl, Instant audioUrlExpiresAt) {}
}
