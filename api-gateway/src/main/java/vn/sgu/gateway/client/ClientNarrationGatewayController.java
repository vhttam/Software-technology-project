package vn.sgu.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/client/pois/{qrCode}/narrations")
public class ClientNarrationGatewayController {
    private static final ParameterizedTypeReference<SuccessEnvelope<PoiSnapshot>> POI_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SuccessEnvelope<PublishedNarrations>> LIST_RESPONSE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<SuccessEnvelope<NarrationArtifact>> NARRATION_RESPONSE =
            new ParameterizedTypeReference<>() {};

    private final WebClient content;
    private final WebClient narration;
    private final String internalToken;
    private final boolean mocksEnabled;

    public ClientNarrationGatewayController(WebClient.Builder builder,
            @Value("${gateway.services.content-url}") String contentUrl,
            @Value("${gateway.services.narration-url}") String narrationUrl,
            @Value("${gateway.internal-token}") String internalToken,
            @Value("${gateway.client.mocks.enabled:true}") boolean mocksEnabled) {
        this.content = builder.baseUrl(contentUrl).build();
        this.narration = builder.baseUrl(narrationUrl).build();
        this.internalToken = internalToken;
        this.mocksEnabled = mocksEnabled;
    }

    @GetMapping
    public Mono<SuccessEnvelope<NarrationList>> list(@PathVariable String qrCode, ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        return activePoi(qrCode, correlationId).flatMap(poi -> publishedNarrations(poi.contentId(), correlationId)
                .map(published -> SuccessEnvelope.success(new NarrationList(
                        poi.poiId(), poi.poiName(), poi.contentTitle(), published.narrations()))));
    }

    @GetMapping("/{lang}")
    public Mono<SuccessEnvelope<ClientNarration>> get(@PathVariable String qrCode, @PathVariable String lang,
                                                       ServerWebExchange exchange) {
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        return activePoi(qrCode, correlationId).flatMap(poi -> narrationFor(poi.contentId(), lang, correlationId)
                .map(artifact -> SuccessEnvelope.success(new ClientNarration(
                        poi.poiId(), poi.poiName(), poi.contentTitle(), artifact.lang(), artifact.version(),
                        artifact.subtitle(), artifact.audioUrl(), artifact.audioUrlExpiresAt()))));
    }

    private Mono<PoiSnapshot> activePoi(String qrCode, String correlationId) {
        if (mocksEnabled) {
            if (qrCode.startsWith("missing-")) {
                return Mono.error(new ClientGatewayException(HttpStatus.NOT_FOUND,
                        "POI_NOT_FOUND", "Điểm tham quan hoặc nội dung không tồn tại"));
            }
            return Mono.just(new PoiSnapshot("poi-demo", "Điểm tham quan mẫu", "content-demo",
                    "Thuyết minh mẫu", false));
        }
        return unwrap(content.get().uri("/internal/pois/by-qr/{qrCode}", qrCode)
                        .header("X-Service-Token", internalToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(POI_RESPONSE),
                "CONTENT_SERVICE_UNAVAILABLE", "POI_NOT_FOUND", "Điểm tham quan hoặc nội dung không tồn tại")
                .flatMap(poi -> poi.contentId() == null || !Boolean.FALSE.equals(poi.isDeleted())
                        ? Mono.error(new ClientGatewayException(HttpStatus.NOT_FOUND,
                                "POI_NOT_FOUND", "Điểm tham quan hoặc nội dung không tồn tại"))
                        : Mono.just(poi));
    }

    private Mono<PublishedNarrations> publishedNarrations(String contentId, String correlationId) {
        return unwrap(narration.get().uri(uriBuilder -> uriBuilder.path("/internal/narrations")
                                .queryParam("contentId", contentId).build())
                        .header("X-Gateway-Service-Token", internalToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(LIST_RESPONSE),
                "NARRATION_SERVICE_UNAVAILABLE", "NARRATION_NOT_FOUND", "Chưa có thuyết minh PUBLISHED");
    }

    private Mono<NarrationArtifact> narrationFor(String contentId, String lang, String correlationId) {
        return unwrap(narration.get().uri(uriBuilder -> uriBuilder.path("/internal/narrations/{lang}")
                                .queryParam("contentId", contentId).build(lang))
                        .header("X-Gateway-Service-Token", internalToken)
                        .header("X-Correlation-ID", correlationId)
                        .retrieve().bodyToMono(NARRATION_RESPONSE),
                "NARRATION_SERVICE_UNAVAILABLE", "NARRATION_NOT_FOUND", "Chưa có thuyết minh PUBLISHED cho ngôn ngữ đã chọn");
    }

    private <T> Mono<T> unwrap(Mono<SuccessEnvelope<T>> response, String unavailableCode,
                               String notFoundCode, String notFoundMessage) {
        return response.flatMap(envelope -> envelope == null || envelope.data() == null
                        ? Mono.error(new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc trả dữ liệu không hợp lệ"))
                        : Mono.just(envelope.data()))
                .onErrorMap(WebClientResponseException.NotFound.class,
                        ex -> new ClientGatewayException(HttpStatus.NOT_FOUND, notFoundCode, notFoundMessage))
                .onErrorMap(WebClientResponseException.class,
                        ex -> new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc tạm thời không khả dụng"))
                .onErrorMap(WebClientRequestException.class,
                        ex -> new ClientGatewayException(HttpStatus.SERVICE_UNAVAILABLE,
                                unavailableCode, "Dịch vụ phụ thuộc tạm thời không khả dụng"));
    }

    public record SuccessEnvelope<T>(String status, T data) {
        static <T> SuccessEnvelope<T> success(T data) { return new SuccessEnvelope<>("success", data); }
    }
    public record PoiSnapshot(String poiId, String poiName, String contentId, String contentTitle, Boolean isDeleted) {}
    public record LanguageView(String lang, int version) {}
    public record PublishedNarrations(List<LanguageView> narrations) {}
    public record NarrationArtifact(String lang, int version, String subtitle, String audioUrl, Instant audioUrlExpiresAt) {}
    public record NarrationList(String poiId, String poiName, String contentTitle, List<LanguageView> narrations) {}
    public record ClientNarration(String poiId, String poiName, String contentTitle, String lang, int version,
                                  String subtitle, String audioUrl, Instant audioUrlExpiresAt) {}
}

