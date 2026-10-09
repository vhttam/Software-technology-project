package vn.sgu.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityConfigurationTest {
    private final JwtSecurityConfiguration configuration = new JwtSecurityConfiguration();

    @Test
    void converterUsesUserIdAndNormalizesRoleClaim() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("subject-id").claim("userId", "user-1").claim("role", "CONTENT_ADMIN").build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) configuration
                .jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CONTENT_ADMIN");
    }

    @Test
    void converterFallsBackToSubjectAndFirstRolesClaim() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("subject-id").claim("roles", List.of("ROLE_CONTENT_ADMIN", "OTHER")).build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) configuration
                .jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication.getName()).isEqualTo("subject-id");
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_CONTENT_ADMIN");
    }

    @Test
    void converterLeavesAuthoritiesEmptyWhenRoleMissing() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject-id").build();
        JwtAuthenticationToken authentication = (JwtAuthenticationToken) configuration
                .jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication.getName()).isEqualTo("subject-id");
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void queryAccessTokenIsAcceptedOnlyForJobEventStream() {
        var converter = configuration.bearerTokenConverter();
        var sse = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/jobs/job-1/events?accessToken=sse-token").build());
        var ordinaryApi = MockServerWebExchange.from(MockServerHttpRequest
                .get("/api/v1/jobs/job-1?accessToken=query-token").build());

        var sseAuthentication = converter.convert(sse).block();
        var ordinaryAuthentication = converter.convert(ordinaryApi).block();

        assertThat(sseAuthentication).isInstanceOf(BearerTokenAuthenticationToken.class);
        assertThat(((BearerTokenAuthenticationToken) sseAuthentication).getToken()).isEqualTo("sse-token");
        assertThat(ordinaryAuthentication).isNull();
    }
}
