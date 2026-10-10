package vn.sgu.gateway.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityConfigurationTest {
    private final JwtSecurityConfiguration configuration = new JwtSecurityConfiguration();

    @Test
    void converterUsesSubjectInsteadOfUserIdAndNormalizesRoleClaim() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("subject-id").claim("userId", "user-1").claim("role", "CONTENT_ADMIN").build();

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) configuration
                .jwtAuthenticationConverter().convert(jwt).block();

        assertThat(authentication.getName()).isEqualTo("subject-id");
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
    void cachesJwksAndRefreshesOnceWhenAnUncachedKidArrives() throws Exception {
        KeyPair firstKey = rsaKeyPair();
        KeyPair rotatedKey = rsaKeyPair();
        AtomicReference<String> jwks = new AtomicReference<>(jwkSet("kid-1", firstKey));
        AtomicInteger requestCount = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            requestCount.incrementAndGet();
            byte[] body = jwks.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            String jwksUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
            var decoder = configuration.jwtDecoder(new ByteArrayResource(new byte[0]), jwksUri);

            assertThat(decoder.decode(signedToken("kid-1", firstKey)).block().getSubject())
                    .isEqualTo("subject-id");
            assertThat(decoder.decode(signedToken("kid-1", firstKey)).block().getSubject())
                    .isEqualTo("subject-id");
            assertThat(requestCount).hasValue(1);

            jwks.set(jwkSet("kid-1", firstKey, "kid-2", rotatedKey));
            assertThat(decoder.decode(signedToken("kid-2", rotatedKey)).block().getSubject())
                    .isEqualTo("subject-id");
            assertThat(decoder.decode(signedToken("kid-2", rotatedKey)).block().getSubject())
                    .isEqualTo("subject-id");
            assertThat(requestCount).hasValue(2);
        } finally {
            server.stop(0);
        }
    }

    private static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String jwkSet(String firstKid, KeyPair firstKey) {
        return new JWKSet(publicJwk(firstKid, firstKey)).toString();
    }

    private static String jwkSet(String firstKid, KeyPair firstKey, String secondKid, KeyPair secondKey) {
        return new JWKSet(List.of(publicJwk(firstKid, firstKey), publicJwk(secondKid, secondKey))).toString();
    }

    private static RSAKey publicJwk(String kid, KeyPair pair) {
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
    }

    private static String signedToken(String kid, KeyPair pair) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject("subject-id")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300))).build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        token.sign(new RSASSASigner(pair.getPrivate()));
        return token.serialize();
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
