package vn.edu.cnpm.gateway;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRbacMatrixTest {

    private static final String JWKS_PATH = "/api/v1/auth/.well-known/jwks.json";
    private static final KeyPair BASE_KEY = rsaKeyPair();
    private static final MockWebServer authServer = new MockWebServer();
    private static final MockWebServer contentServer = new MockWebServer();
    private static final MockWebServer narrationServer = new MockWebServer();

    static {
        Dispatcher downstreamDispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith(JWKS_PATH)) {
                    return new MockResponse()
                            .setResponseCode(200)
                            .addHeader("Content-Type", "application/json")
                            .setBody(jwks(BASE_KEY, "matrix-test-key"));
                }
                String m = request.getMethod();
                int status = "POST".equalsIgnoreCase(m) ? 201 : 200;
                return new MockResponse()
                        .setResponseCode(status)
                        .addHeader("Content-Type", "application/json")
                        .setBody("{\"status\":\"success\",\"data\":{}}");
            }
        };

        authServer.setDispatcher(downstreamDispatcher);
        contentServer.setDispatcher(downstreamDispatcher);
        narrationServer.setDispatcher(downstreamDispatcher);

        try {
            authServer.start();
            contentServer.start();
            narrationServer.start();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.auth-service-url", () -> authServer.url("/").toString());
        registry.add("gateway.content-service-url", () -> contentServer.url("/").toString());
        registry.add("gateway.narration-service-url", () -> narrationServer.url("/").toString());
        registry.add("gateway.service-token", () -> "gateway-test-secret");
        registry.add("NARRATION_SERVICE_TOKEN", () -> "narration-test-secret");
    }

    @AfterAll
    static void stopServers() throws IOException {
        authServer.shutdown();
        contentServer.shutdown();
        narrationServer.shutdown();
    }

    @Autowired
    private WebTestClient client;

    @ParameterizedTest(name = "{0} [{1}] -> expected {5}")
    @MethodSource("rbacMatrixCases")
    void verifyRbacMatrixCell(String endpointDesc, String role, HttpMethod method, String path, String body, int expectedStatus) {
        String token = "UNAUTHENTICATED".equals(role) ? null : generateToken(role);

        boolean isSse = path.contains("/events");
        // SSE streams never complete — use a short-timeout client so we don't block forever.
        // The status header IS flushed immediately thanks to the ": connected" initial SSE comment.
        WebTestClient effectiveClient = isSse
                ? client.mutate().responseTimeout(java.time.Duration.ofSeconds(2)).build()
                : client;

        WebTestClient.RequestHeadersSpec<?> req;
        if (isSse) {
            String uri = (token != null) ? (path + "?accessToken=" + token) : path;
            req = effectiveClient.method(method).uri(uri);
        } else {
            WebTestClient.RequestBodySpec bodySpec = effectiveClient.method(method).uri(path);
            if (token != null) {
                bodySpec.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                bodySpec.contentType(org.springframework.http.MediaType.APPLICATION_JSON);
                bodySpec.bodyValue(body);
            }
            req = bodySpec;
        }

        if (isSse && expectedStatus == 200) {
            // For successful SSE connections, the stream never ends. We verify the status
            // from the FluxExchangeResult (status header is flushed before body streaming).
            var fluxResult = req.exchange().returnResult(String.class);
            assertThat(fluxResult.getStatus().value())
                    .as("Endpoint %s with role %s expected status %d but was %d", endpointDesc, role, expectedStatus, fluxResult.getStatus().value())
                    .isEqualTo(expectedStatus);
            // Don't read body — SSE stream is infinite. Dispose subscription immediately.
            fluxResult.getResponseBody().subscribe().dispose();
        } else {
            var result = req.exchange().returnResult(Void.class);
            assertThat(result.getStatus().value())
                    .as("Endpoint %s with role %s expected status %d but was %d", endpointDesc, role, expectedStatus, result.getStatus().value())
                    .isEqualTo(expectedStatus);
        }
    }

    static Stream<Arguments> rbacMatrixCases() {
        List<EndpointDef> endpoints = List.of(
                new EndpointDef("GET /api/v1/auth/.well-known/jwks.json", HttpMethod.GET, "/api/v1/auth/.well-known/jwks.json", null, 200, 200, 200),
                new EndpointDef("POST /api/v1/auth/login", HttpMethod.POST, "/api/v1/auth/login", "{\"username\":\"u\",\"password\":\"p\"}", 201, 201, 201),
                new EndpointDef("POST /api/v1/auth/refresh", HttpMethod.POST, "/api/v1/auth/refresh", "{\"refreshToken\":\"r\"}", 201, 201, 201),
                new EndpointDef("GET /api/v1/auth/users", HttpMethod.GET, "/api/v1/auth/users", null, 200, 403, 401),
                new EndpointDef("POST /api/v1/auth/users", HttpMethod.POST, "/api/v1/auth/users", "{\"username\":\"u\",\"password\":\"P@ss1234\"}", 201, 403, 401),
                new EndpointDef("PUT /api/v1/auth/password", HttpMethod.PUT, "/api/v1/auth/password", "{\"currentPassword\":\"P1\",\"newPassword\":\"P2\"}", 200, 200, 401),
                new EndpointDef("PUT /api/v1/auth/users/{id}/reset-password", HttpMethod.PUT, "/api/v1/auth/users/user-123/reset-password", "{\"newPassword\":\"P2\"}", 200, 403, 401),
                new EndpointDef("PUT /api/v1/auth/users/{id}/unlock", HttpMethod.PUT, "/api/v1/auth/users/user-123/unlock", "{}", 200, 403, 401),
                new EndpointDef("PUT /api/v1/auth/users/{id}/disable", HttpMethod.PUT, "/api/v1/auth/users/user-123/disable", "{}", 200, 403, 401),
                new EndpointDef("PUT /api/v1/auth/users/{id}/enable", HttpMethod.PUT, "/api/v1/auth/users/user-123/enable", "{}", 200, 403, 401),
                new EndpointDef("GET /api/v1/languages/config", HttpMethod.GET, "/api/v1/languages/config", null, 200, 200, 401),
                new EndpointDef("GET /api/v1/pois", HttpMethod.GET, "/api/v1/pois", null, 200, 200, 401),
                new EndpointDef("POST /api/v1/pois", HttpMethod.POST, "/api/v1/pois", "{\"name\":\"Poi\"}", 201, 201, 401),
                new EndpointDef("POST /api/v1/pois/{id}/contents", HttpMethod.POST, "/api/v1/pois/poi-123/contents", "{\"title\":\"T\"}", 201, 201, 401),
                new EndpointDef("GET /api/v1/pois/{id}/contents/active", HttpMethod.GET, "/api/v1/pois/poi-123/contents/active", null, 200, 200, 401),
                new EndpointDef("GET /api/v1/voices", HttpMethod.GET, "/api/v1/voices", null, 200, 200, 401),
                new EndpointDef("GET /api/v1/jobs", HttpMethod.GET, "/api/v1/jobs", null, 200, 200, 401),
                new EndpointDef("POST /api/v1/jobs", HttpMethod.POST, "/api/v1/jobs", "{\"contentId\":\"c-1\",\"targets\":[]}", 201, 201, 401),
                new EndpointDef("GET /api/v1/jobs/{id}", HttpMethod.GET, "/api/v1/jobs/job-123", null, 200, 200, 401),
                new EndpointDef("POST /api/v1/jobs/{id}/cancel", HttpMethod.POST, "/api/v1/jobs/job-123/cancel", null, 201, 201, 401),
                new EndpointDef("POST /api/v1/jobs/batch-translate", HttpMethod.POST, "/api/v1/jobs/batch-translate", "{\"targetLang\":\"en\"}", 201, 201, 401),
                new EndpointDef("GET /api/v1/jobs/{id}/events", HttpMethod.GET, "/api/v1/jobs/job-123/events", null, 200, 200, 401)
        );

        List<Arguments> cases = new ArrayList<>();
        for (EndpointDef ep : endpoints) {
            cases.add(Arguments.of(ep.name, "SUPER_ADMIN", ep.method, ep.path, ep.body, ep.superAdminStatus));
            cases.add(Arguments.of(ep.name, "CONTENT_ADMIN", ep.method, ep.path, ep.body, ep.contentAdminStatus));
            cases.add(Arguments.of(ep.name, "UNAUTHENTICATED", ep.method, ep.path, ep.body, ep.unauthStatus));
        }
        return cases.stream();
    }

    private static String generateToken(String role) {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test-user-id")
                .claim("role", role)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("matrix-test-key").build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner((java.security.interfaces.RSAPrivateKey) BASE_KEY.getPrivate()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static String jwks(KeyPair pair, String kid) {
        try {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .keyID(kid).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build();
            return new JWKSet(jwk).toString();
        } catch (Exception ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    private record EndpointDef(String name, HttpMethod method, String path, String body,
                               int superAdminStatus, int contentAdminStatus, int unauthStatus) {
    }
}
