package vn.sgu.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedIdentityGatewayFilterTest {
    private final TrustedIdentityGatewayFilter filter = new TrustedIdentityGatewayFilter("trusted-token");

    @Test
    void replacesSpoofedIdentityWithJwtClaimsAndTrustedServiceToken() {
        Jwt jwt = Jwt.withTokenValue("jwt")
                .header("alg", "RS256")
                .subject("subject-id")
                .claim("userId", "user-7")
                .claim("role", "ROLE_CONTENT_ADMIN")
                .build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/jobs")
                .header("X-User-Id", "spoofed")
                .header("X-User-Role", "ADMIN")
                .header("X-Service-Token", "spoofed-token")
                .build()).mutate().principal(Mono.just(authentication)).build();
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isEqualTo("user-7");
        assertThat(headers.getFirst("X-User-Role")).isEqualTo("CONTENT_ADMIN");
        assertThat(headers.getFirst("X-Service-Token")).isEqualTo("trusted-token");
    }

    @Test
    void stripsClientIdentityWhenNoJwtPrincipalExists() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/client/pois/qr/narrations")
                .header("X-User-Id", "spoofed")
                .header("X-User-Role", "CONTENT_ADMIN")
                .build());
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-User-Role")).isNull();
        assertThat(headers.getFirst("X-Service-Token")).isEqualTo("trusted-token");
    }

    @Test
    void preservesNarrationServiceTokenForInternalProgressCallback() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/internal/callbacks/job-progress")
                .header("X-User-Id", "spoofed")
                .header("X-Service-Token", "narration-token")
                .build());
        AtomicReference<org.springframework.web.server.ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, next -> {
            forwarded.set(next);
            return Mono.empty();
        }).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst("X-User-Id")).isNull();
        assertThat(headers.getFirst("X-Service-Token")).isEqualTo("narration-token");
    }
}
