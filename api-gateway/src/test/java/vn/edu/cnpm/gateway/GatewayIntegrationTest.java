package vn.edu.cnpm.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
class GatewayIntegrationTest {
    private static final String JWKS_PATH = "/api/v1/auth/.well-known/jwks.json";
    private static final String FRONTEND_ORIGIN = "http://frontend.test";
    private static final KeyPair BASE_KEY = rsaKeyPair();
    private static final KeyPair ROTATED_KEY = rsaKeyPair();
    private static final AtomicReference<String> jwksBody = new AtomicReference<>(jwks(BASE_KEY, "test-key"));
    private static final AtomicInteger jwksStatus = new AtomicInteger(200);
    private static final AtomicInteger jwksCalls = new AtomicInteger();
    private static final AtomicInteger jwksDelayMs = new AtomicInteger();
    private static final ConcurrentLinkedQueue<MockResponse> authReplies = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<MockResponse> contentReplies = new ConcurrentLinkedQueue<>();
    private static final MockWebServer authServer = new MockWebServer();
    private static final MockWebServer contentServer = new MockWebServer();

    static {
        authServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getPath() != null && request.getPath().startsWith(JWKS_PATH)) {
                    jwksCalls.incrementAndGet();
                    int delay = jwksDelayMs.get();
                    MockResponse response = new MockResponse()
                            .setResponseCode(jwksStatus.get())
                            .addHeader("Content-Type", "application/json")
                            .setBody(jwksStatus.get() == 200 ? jwksBody.get() : "{}");
                    if (delay > 0) response.setHeadersDelay(delay, TimeUnit.MILLISECONDS);
                    return response;
                }
                MockResponse reply = authReplies.poll();
                return reply == null ? jsonResponse(200, "{\"status\":\"success\",\"data\":{}}") : reply;
            }
        });
        contentServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                MockResponse reply = contentReplies.poll();
                return reply == null ? jsonResponse(200, "{\"status\":\"success\",\"data\":{}}") : reply;
            }
        });
        try {
            authServer.start();
            contentServer.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private WebTestClient client;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("gateway.auth-service-url", () -> authServer.url("/").toString());
        registry.add("gateway.content-service-url", () -> contentServer.url("/").toString());
        registry.add("gateway.cors.allowed-origins", () -> FRONTEND_ORIGIN);
        registry.add("spring.cloud.gateway.server.webflux.httpclient.response-timeout", () -> "2s");
    }

    @BeforeEach
    void resetMocks() {
        authReplies.clear();
        contentReplies.clear();
        jwksBody.set(jwks(BASE_KEY, "test-key"));
        jwksStatus.set(200);
        jwksDelayMs.set(0);
        drain(authServer);
        drain(contentServer);
    }

    @AfterAll
    static void stopServers() throws IOException {
        authServer.shutdown();
        contentServer.shutdown();
    }

    @Test
    void healthStartsWithoutCallingAuthOrContentAndCreatesCorrelationIds() throws Exception {
        int authBefore = authServer.getRequestCount();
        int contentBefore = contentServer.getRequestCount();

        EntityExchangeResult<byte[]> first = client.get().uri("/health")
                .header("X-Correlation-ID", "client-chosen")
                .exchange().expectStatus().isOk().expectBody().returnResult();
        EntityExchangeResult<byte[]> second = client.get().uri("/health")
                .exchange().expectStatus().isOk().expectBody().returnResult();

        assertThat(objectMapper.readTree(first.getResponseBodyContent()).get("status").asText()).isEqualTo("UP");
        String firstId = first.getResponseHeaders().getFirst("X-Correlation-ID");
        String secondId = second.getResponseHeaders().getFirst("X-Correlation-ID");
        UUID.fromString(firstId);
        UUID.fromString(secondId);
        assertThat(firstId).isNotEqualTo("client-chosen").isNotEqualTo(secondId);
        assertThat(authServer.getRequestCount()).isEqualTo(authBefore);
        assertThat(contentServer.getRequestCount()).isEqualTo(contentBefore);
    }

    @Test
    void everyContentRouteForwardsMethodPathAndTrustedIdentity() throws Exception {
        List<RouteCase> routes = List.of(
                new RouteCase(HttpMethod.GET, "/api/v1/languages/source"),
                new RouteCase(HttpMethod.GET, "/api/v1/pois"),
                new RouteCase(HttpMethod.POST, "/api/v1/pois/p-1/contents"),
                new RouteCase(HttpMethod.GET, "/api/v1/pois/p-1/contents/active"),
                new RouteCase(HttpMethod.GET, "/api/v1/contents"),
                new RouteCase(HttpMethod.GET, "/api/v1/contents/c-1"),
                new RouteCase(HttpMethod.PUT, "/api/v1/contents/c-1"),
                new RouteCase(HttpMethod.DELETE, "/api/v1/contents/c-1"));

        for (RouteCase route : routes) {
            String forwardedBody = route.method() == HttpMethod.POST || route.method() == HttpMethod.PUT
                    ? "{\"title\":\"forwarded unchanged\"}" : null;
            java.util.function.Consumer<HttpHeaders> spoofedHeaders = headers -> {
                        headers.set("X-User-Id", "forged-user");
                        headers.set("X-User-Role", "SUPER_ADMIN");
                        headers.set("X-Service-Token", "forged-service-secret");
                        headers.set("X-Correlation-ID", "forged-correlation");
                        headers.set("Idempotency-Key", "keep-this-header");
                    };
            EntityExchangeResult<byte[]> response = forwardedBody == null
                    ? send(route.method(), route.path(), token("CONTENT_ADMIN"), spoofedHeaders)
                    : sendWithBody(route.method(), route.path(), token("CONTENT_ADMIN"), forwardedBody, spoofedHeaders);
            assertThat(response.getStatus().value()).isEqualTo(200);
            RecordedRequest downstream = contentServer.takeRequest(2, TimeUnit.SECONDS);
            assertNotNull(downstream);
            assertThat(downstream.getMethod()).isEqualTo(route.method().name());
            assertThat(downstream.getPath()).isEqualTo(route.path());
            assertThat(downstream.getHeader("X-User-Id")).isEqualTo("admin-1");
            assertThat(downstream.getHeader("X-User-Role")).isEqualTo("CONTENT_ADMIN");
            assertThat(downstream.getHeader("X-Correlation-ID")).isEqualTo(
                    response.getResponseHeaders().getFirst("X-Correlation-ID"));
            assertThat(downstream.getHeader("Authorization")).isNull();
            assertThat(downstream.getHeader("X-Service-Token")).isNull();
            assertThat(downstream.getHeader("Idempotency-Key")).isEqualTo("keep-this-header");
            if (forwardedBody != null) assertThat(downstream.getBody().readUtf8()).isEqualTo(forwardedBody);
        }
    }

    @Test
    void searchQueryAndRequestBodyAreForwardedWithoutGatewayBusinessValidation() throws Exception {
        String query = "/api/v1/contents?page=1&pageSize=20&query=bao-tang";
        String body = "{\"title\":\"  \",\"sourceLang\":\"unsupported\",\"textContent\":\"BODY_SENTINEL\"}";
        contentReplies.add(jsonResponse(400, "{\"status\":\"error\",\"errorCode\":\"VALIDATION_ERROR\","
                + "\"message\":\"Dữ liệu không hợp lệ\",\"details\":{\"title\":[\"invalid\"]},"
                + "\"correlationId\":\"downstream-id\"}"));

        EntityExchangeResult<byte[]> response = client.post().uri("/api/v1/pois/p-9/contents")
                .header("Authorization", "Bearer " + token("CONTENT_ADMIN"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(body).exchange().expectStatus().isBadRequest().expectBody().returnResult();
        RecordedRequest create = contentServer.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(create);
        assertThat(create.getPath()).isEqualTo("/api/v1/pois/p-9/contents");
        assertThat(create.getBody().readUtf8()).isEqualTo(body);
        assertThat(new String(response.getResponseBodyContent())).contains("VALIDATION_ERROR", "downstream-id");

        contentReplies.add(jsonResponse(200, "{\"status\":\"success\",\"data\":{\"items\":[]}}"));
        EntityExchangeResult<byte[]> search = send(HttpMethod.GET, query, token("CONTENT_ADMIN"),
                headers -> headers.setOrigin(FRONTEND_ORIGIN));
        RecordedRequest list = contentServer.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(list);
        assertThat(list.getPath()).isEqualTo(query);
        assertThat(search.getStatus().value()).isEqualTo(200);
        assertThat(search.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo(FRONTEND_ORIGIN);
    }

    @Test
    void downstreamResponsesRemainTransparentIncludingNoContentAndConflictDetails() throws Exception {
        List<DownstreamReply> replies = List.of(
                new DownstreamReply(201, "{\"created\":true}"),
                new DownstreamReply(200, "{\"status\":\"success\"}"),
                new DownstreamReply(204, ""),
                new DownstreamReply(400, "{\"errorCode\":\"VALIDATION_ERROR\",\"details\":{\"textContent\":[\"bad\"]}}"),
                new DownstreamReply(404, "{\"errorCode\":\"CONTENT_NOT_FOUND\"}"),
                new DownstreamReply(409, "{\"errorCode\":\"ACTIVE_CONTENT_EXISTS\"}"),
                new DownstreamReply(409, "{\"errorCode\":\"CONTENT_VERSION_CONFLICT\",\"currentVersion\":4}"));

        for (DownstreamReply reply : replies) {
            contentReplies.add(jsonResponse(reply.status(), reply.body()));
            EntityExchangeResult<byte[]> response = send(HttpMethod.GET, "/api/v1/contents", token("CONTENT_ADMIN"), h -> { });
            assertThat(response.getStatus().value()).isEqualTo(reply.status());
            if (reply.status() == 204) assertThat(response.getResponseBodyContent()).isNullOrEmpty();
            else assertThat(new String(response.getResponseBodyContent())).isEqualTo(reply.body());
            assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void downstreamGatewayStatusesAndConnectionResetBecomeContentUnavailable() throws Exception {
        for (int status : List.of(502, 503, 504)) {
            contentReplies.add(jsonResponse(status, "downstream detail must not leak"));
            assertServiceUnavailable(send(HttpMethod.GET, "/api/v1/pois", token("CONTENT_ADMIN"), h -> { }),
                    "CONTENT_SERVICE_UNAVAILABLE");
            assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));
        }

        contentReplies.add(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));
        EntityExchangeResult<byte[]> reset = send(HttpMethod.POST, "/api/v1/pois/p-1/contents",
                token("CONTENT_ADMIN"), h -> { });
        assertServiceUnavailable(reset, "CONTENT_SERVICE_UNAVAILABLE");
    }

    @Test
    void downstreamResponseTimeoutBecomesContentUnavailable() throws Exception {
        contentReplies.add(jsonResponse(200, "late response").setHeadersDelay(3500, TimeUnit.MILLISECONDS));
        EntityExchangeResult<byte[]> timeout = send(HttpMethod.GET, "/api/v1/pois", token("CONTENT_ADMIN"), h -> { });
        assertServiceUnavailable(timeout, "CONTENT_SERVICE_UNAVAILABLE");
        assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));
    }

    @Test
    void authRoutesArePublicExceptLogoutAndForwardAuthErrors() throws Exception {
        authReplies.add(jsonResponse(401, "{\"status\":\"error\",\"errorCode\":\"INVALID_CREDENTIALS\"}"));
        EntityExchangeResult<byte[]> login = client.post().uri("/api/v1/auth/login")
                .header("Authorization", "Bearer client-token-must-not-pass")
                .header("X-User-Id", "forged-user")
                .header("X-User-Role", "SUPER_ADMIN")
                .header("X-Service-Token", "forged-service-secret")
                .header("X-Correlation-ID", "client-selected-correlation")
                .bodyValue("{\"username\":\"admin\",\"password\":\"PASSWORD_SENTINEL\"}")
                .exchange().expectStatus().isUnauthorized().expectBody().returnResult();
        RecordedRequest loginRequest = authServer.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(loginRequest);
        assertThat(loginRequest.getPath()).isEqualTo("/api/v1/auth/login");
        assertThat(loginRequest.getHeader("Authorization")).isNull();
        assertThat(loginRequest.getHeader("X-User-Id")).isNull();
        assertThat(loginRequest.getHeader("X-User-Role")).isNull();
        assertThat(loginRequest.getHeader("X-Service-Token")).isNull();
        assertThat(loginRequest.getHeader("X-Correlation-ID"))
                .isEqualTo(login.getResponseHeaders().getFirst("X-Correlation-ID"));
        assertThat(new String(login.getResponseBodyContent())).contains("INVALID_CREDENTIALS");

        authReplies.add(jsonResponse(200, "{\"status\":\"success\",\"data\":{\"accessToken\":\"new\"}}"));
        assertThat(send(HttpMethod.POST, "/api/v1/auth/refresh", null, h -> { }).getStatus().value()).isEqualTo(200);
        assertThat(authServer.takeRequest(2, TimeUnit.SECONDS).getPath()).isEqualTo("/api/v1/auth/refresh");

        assertThat(send(HttpMethod.GET, JWKS_PATH, null, h -> { }).getStatus().value()).isEqualTo(200);
        assertThat(authServer.takeRequest(2, TimeUnit.SECONDS).getPath()).isEqualTo(JWKS_PATH);

        EntityExchangeResult<byte[]> missingToken = send(HttpMethod.POST, "/api/v1/auth/logout", null, h -> { });
        assertThat(missingToken.getStatus().value()).isEqualTo(401);
        authReplies.add(new MockResponse().setResponseCode(204));
        EntityExchangeResult<byte[]> logout = send(HttpMethod.POST, "/api/v1/auth/logout", token("CONTENT_ADMIN"), h -> { });
        assertThat(logout.getStatus().value()).isEqualTo(204);
        RecordedRequest logoutRequest = takeRequestForPath(authServer, "/api/v1/auth/logout");
        assertNotNull(logoutRequest);
        assertThat(logoutRequest.getPath()).isEqualTo("/api/v1/auth/logout");
        assertThat(logoutRequest.getHeader("Authorization")).isNull();
        assertThat(logoutRequest.getHeader("X-User-Id")).isEqualTo("admin-1");

        authReplies.add(new MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST));
        EntityExchangeResult<byte[]> unavailable = send(HttpMethod.POST, "/api/v1/auth/login", null, h -> { });
        assertServiceUnavailable(unavailable, "AUTH_SERVICE_UNAVAILABLE");
    }

    @Test
    void malformedExpiredAndWrongAlgorithmTokensAlwaysReturnUnauthorized() throws Exception {
        KeyPair wrongPair = rsaKeyPair();
        String validClaims = signedToken(BASE_KEY, "test-key", "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, true, true, true);
        String expired = signedToken(BASE_KEY, "test-key", "CONTENT_ADMIN", Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(60), true, true, true, true);
        String wrongSignature = signedToken(wrongPair, "test-key", "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, true, true, true);
        String noKid = signedToken(BASE_KEY, null, "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, true, true, true);
        String userIdOnly = signedToken(BASE_KEY, "test-key", "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), false, true, true, true);
        String missingRole = signedToken(BASE_KEY, "test-key", null, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, false, true, true);
        String missingIat = signedToken(BASE_KEY, "test-key", "CONTENT_ADMIN", null,
                Instant.now().plusSeconds(600), true, true, false, true);
        String missingExp = signedToken(BASE_KEY, "test-key", "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                null, true, true, true, false);
        String none = new PlainJWT(new JWTClaimsSet.Builder().subject("admin-1").build()).serialize();
        String hs = hmacTokenUsingPublicKeyBytes();
        List<String> invalidAuthorizations = List.of("", "Basic abc", "Bearer broken", "Bearer " + expired,
                "Bearer " + wrongSignature, "Bearer " + noKid, "Bearer " + userIdOnly,
                "Bearer " + missingRole, "Bearer " + missingIat, "Bearer " + missingExp,
                "Bearer " + none, "Bearer " + hs);

        int contentBefore = contentServer.getRequestCount();
        for (String authorization : invalidAuthorizations) {
            EntityExchangeResult<byte[]> response = send(HttpMethod.GET, "/api/v1/contents", null, headers -> {
                if (!authorization.isEmpty()) headers.set("Authorization", authorization);
            });
            assertThat(response.getStatus().value()).isEqualTo(401);
            assertThat(objectMapper.readTree(response.getResponseBodyContent()).get("errorCode").asText())
                    .isEqualTo("UNAUTHORIZED");
        }
        EntityExchangeResult<byte[]> accepted = send(HttpMethod.GET, "/api/v1/contents", "Bearer " + validClaims, h -> { });
        assertThat(accepted.getStatus().value()).isEqualTo(200);
        assertThat(contentServer.getRequestCount() - contentBefore).isEqualTo(1);
    }

    @Test
    void wrongRoleIsForbiddenAndUnknownKidRefreshesJwksOnlyOnce() throws Exception {
        int contentBefore = contentServer.getRequestCount();
        EntityExchangeResult<byte[]> forbidden = send(HttpMethod.GET, "/api/v1/contents", token("VIEWER"), h -> { });
        assertThat(forbidden.getStatus().value()).isEqualTo(403);
        assertThat(objectMapper.readTree(forbidden.getResponseBodyContent()).get("errorCode").asText()).isEqualTo("FORBIDDEN");
        assertThat(contentServer.getRequestCount()).isEqualTo(contentBefore);

        String rotatedKid = "rotated-" + UUID.randomUUID();
        jwksBody.set(jwks(ROTATED_KEY, rotatedKid));
        int callsBefore = jwksCalls.get();
        EntityExchangeResult<byte[]> rotated = send(HttpMethod.GET, "/api/v1/contents",
                signedToken(ROTATED_KEY, rotatedKid, "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                        Instant.now().plusSeconds(600), true, true, true, true), h -> { });
        assertThat(rotated.getStatus().value()).isEqualTo(200);
        assertThat(jwksCalls.get() - callsBefore).isEqualTo(1);
        assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));

        jwksBody.set(jwks(BASE_KEY, "test-key"));
        String missingKid = "missing-" + UUID.randomUUID();
        int beforeUnknown = jwksCalls.get();
        EntityExchangeResult<byte[]> unknown = send(HttpMethod.GET, "/api/v1/contents",
                signedToken(ROTATED_KEY, missingKid, "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                        Instant.now().plusSeconds(600), true, true, true, true), h -> { });
        assertThat(unknown.getStatus().value()).isEqualTo(401);
        assertThat(jwksCalls.get() - beforeUnknown).isEqualTo(1);
    }

    @Test
    void authFailureDuringUnknownKidRefreshIs503ButCachedKeysStillWork() throws Exception {
        String unknownKid = "auth-down-" + UUID.randomUUID();
        jwksStatus.set(503);
        EntityExchangeResult<byte[]> unavailable = send(HttpMethod.GET, "/api/v1/contents",
                signedToken(ROTATED_KEY, unknownKid, "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                        Instant.now().plusSeconds(600), true, true, true, true), h -> { });
        assertServiceUnavailable(unavailable, "AUTH_SERVICE_UNAVAILABLE");

        jwksStatus.set(200);
        jwksBody.set(jwks(BASE_KEY, "test-key"));
        EntityExchangeResult<byte[]> cached = send(HttpMethod.GET, "/api/v1/contents", token("CONTENT_ADMIN"), h -> { });
        assertThat(cached.getStatus().value()).isEqualTo(200);
        assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));
    }

    @Test
    void concurrentRequestsForOneUnknownKidShareSingleJwksReload() throws Exception {
        String kid = "concurrent-" + UUID.randomUUID();
        jwksBody.set(jwks(ROTATED_KEY, kid));
        jwksDelayMs.set(250);
        String token = signedToken(ROTATED_KEY, kid, "CONTENT_ADMIN", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, true, true, true);
        int before = jwksCalls.get();

        List<CompletableFuture<EntityExchangeResult<byte[]>>> calls = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> CompletableFuture.supplyAsync(() -> send(HttpMethod.GET,
                        "/api/v1/contents", token, h -> { }))).toList();
        CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get(15, TimeUnit.SECONDS);

        assertThat(calls).allSatisfy(call -> assertThat(call.join().getStatus().value()).isEqualTo(200));
        assertThat(jwksCalls.get() - before).isEqualTo(1);
    }

    @Test
    void unsupportedPathsAndMethodsAreJson404AndOnlyRealRoutePreflightPasses() throws Exception {
        for (String path : List.of("/internal/pois", "/api/v1/client/pois", "/not-a-route")) {
            EntityExchangeResult<byte[]> response = send(HttpMethod.GET, path, null, h -> { });
            assertThat(response.getStatus().value()).isEqualTo(404);
            assertThat(objectMapper.readTree(response.getResponseBodyContent()).get("errorCode").asText())
                    .isEqualTo("ROUTE_NOT_FOUND");
            UUID.fromString(response.getResponseHeaders().getFirst("X-Correlation-ID"));
        }
        assertThat(send(HttpMethod.POST, "/api/v1/pois", token("CONTENT_ADMIN"), h -> { }).getStatus().value())
                .isEqualTo(404);

        EntityExchangeResult<byte[]> preflight = client.options().uri("/api/v1/pois")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange().expectStatus().isNoContent().expectBody().returnResult();
        assertThat(preflight.getResponseHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .isEqualTo(FRONTEND_ORIGIN);
        assertThat(preflight.getResponseHeaders().getFirst("X-Correlation-ID")).isNotBlank();

        EntityExchangeResult<byte[]> invalidPreflight = client.options().uri("/internal/pois")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange().expectStatus().isNotFound().expectBody().returnResult();
        assertThat(objectMapper.readTree(invalidPreflight.getResponseBodyContent()).get("errorCode").asText())
                .isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test
    void accessTokenPasswordAndBodyAreNeverWrittenToRequestLogs(CapturedOutput output) throws Exception {
        String secretToken = token("CONTENT_ADMIN");
        contentReplies.add(jsonResponse(200, "{\"ok\":true}"));
        send(HttpMethod.POST, "/api/v1/pois/p-1/contents?accessToken=QUERY_SENTINEL", secretToken,
                headers -> headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON));
        assertNotNull(contentServer.takeRequest(2, TimeUnit.SECONDS));
        assertThat(output.getAll()).doesNotContain(secretToken, "PASSWORD_SENTINEL", "BODY_SENTINEL", "QUERY_SENTINEL");
    }

    private EntityExchangeResult<byte[]> send(HttpMethod method, String path, String authorization,
                                               java.util.function.Consumer<HttpHeaders> headers) {
        WebTestClient.RequestBodySpec request = client.method(method).uri(path);
        request.headers(httpHeaders -> {
            if (authorization != null) {
                String value = authorization.startsWith("Bearer ") ? authorization : "Bearer " + authorization;
                httpHeaders.set("Authorization", value);
            }
            headers.accept(httpHeaders);
        });
        return request.exchange().expectBody().returnResult();
    }

    private EntityExchangeResult<byte[]> sendWithBody(HttpMethod method, String path, String authorization,
                                                       String body, java.util.function.Consumer<HttpHeaders> headers) {
        WebTestClient.RequestBodySpec request = client.method(method).uri(path);
        request.headers(httpHeaders -> {
            if (authorization != null) {
                String value = authorization.startsWith("Bearer ") ? authorization : "Bearer " + authorization;
                httpHeaders.set("Authorization", value);
            }
            httpHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.accept(httpHeaders);
        });
        return request.bodyValue(body).exchange().expectBody().returnResult();
    }

    private String token(String role) {
        return signedToken(BASE_KEY, "test-key", role, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(600), true, role != null, true, true);
    }

    private static String signedToken(KeyPair pair, String kid, String role, Instant issuedAt, Instant expiresAt,
                                      boolean withSub, boolean withRole, boolean withIssuedAt, boolean withExpiresAt) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
        if (withSub) claims.subject("admin-1");
        else claims.claim("userId", "admin-1");
        if (withRole && role != null) claims.claim("role", role);
        if (withIssuedAt && issuedAt != null) claims.issueTime(Date.from(issuedAt));
        if (withExpiresAt && expiresAt != null) claims.expirationTime(Date.from(expiresAt));
        try {
            JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256);
            if (kid != null) header.keyID(kid);
            SignedJWT jwt = new SignedJWT(header.build(), claims.build());
            jwt.sign(new RSASSASigner((java.security.interfaces.RSAPrivateKey) pair.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hmacTokenUsingPublicKeyBytes() {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("admin-1").claim("role", "CONTENT_ADMIN")
                    .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("test-key").build(), claims);
            jwt.sign(new MACSigner(((RSAPublicKey) BASE_KEY.getPublic()).getEncoded()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void assertServiceUnavailable(EntityExchangeResult<byte[]> response, String code) throws Exception {
        assertThat(response.getStatus().value()).isEqualTo(503);
        JsonNode envelope = objectMapper.readTree(response.getResponseBodyContent());
        assertThat(envelope.get("status").asText()).isEqualTo("error");
        assertThat(envelope.get("errorCode").asText()).isEqualTo(code);
        assertThat(envelope.get("details").isNull()).isTrue();
        assertThat(envelope.get("correlationId").asText())
                .isEqualTo(response.getResponseHeaders().getFirst("X-Correlation-ID"));
    }

    private static MockResponse jsonResponse(int status, String body) {
        MockResponse response = new MockResponse().setResponseCode(status)
                .addHeader("Content-Type", "application/json");
        if (status != 204 && body != null && !body.isEmpty()) response.setBody(body);
        return response;
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String jwks(KeyPair pair, String kid) {
        try {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .keyID(kid).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build();
            return new JWKSet(jwk).toString();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void drain(MockWebServer server) {
        try {
            while (server.takeRequest(5, TimeUnit.MILLISECONDS) != null) { }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private RecordedRequest takeRequestForPath(MockWebServer server, String expectedPath) throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            if (request == null) return null;
            if (expectedPath.equals(request.getPath())) return request;
        }
        return null;
    }

    private record RouteCase(HttpMethod method, String path) { }
    private record DownstreamReply(int status, String body) { }
}
