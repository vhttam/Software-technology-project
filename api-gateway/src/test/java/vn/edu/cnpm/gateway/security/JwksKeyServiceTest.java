package vn.edu.cnpm.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import vn.edu.cnpm.gateway.config.GatewayProperties;
import vn.edu.cnpm.gateway.error.AuthServiceUnavailableException;
import vn.edu.cnpm.gateway.error.UnknownKeyException;

class JwksKeyServiceTest {
    private MockWebServer authServer;
    private MutableClock clock;
    private GatewayProperties properties;
    private JwksKeyService keys;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        authServer = new MockWebServer();
        authServer.start();
        keyPair = generateKeyPair();
        clock = new MutableClock(Instant.parse("2026-10-11T12:00:00Z"));
        properties = new GatewayProperties();
        properties.setAuthServiceUrl(authServer.url("/").toString());
        properties.setJwksTtl(Duration.ofMinutes(10));
        properties.setUnknownKidTtl(Duration.ofSeconds(30));
        keys = new JwksKeyService(WebClient.builder().baseUrl(properties.getAuthServiceUrl()).build(),
                properties, clock);
    }

    @AfterEach
    void stopServer() throws IOException {
        authServer.shutdown();
    }

    @Test
    void freshKeyIsCachedByKidWithoutAnotherJwksRequest() {
        authServer.enqueue(jwksResponse(keyPair, "known"));

        RSAPublicKey first = keys.keyFor("known", "corr-1").block();
        RSAPublicKey second = keys.keyFor("known", "corr-2").block();

        assertThat(first).isEqualTo(second);
        assertThat(authServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void expiredCachedKeyIsUsedIfAuthServiceCannotRefreshIt() {
        authServer.enqueue(jwksResponse(keyPair, "known"));
        RSAPublicKey cached = keys.keyFor("known", "corr-first").block();
        clock.advance(Duration.ofMinutes(11));
        authServer.enqueue(new MockResponse().setResponseCode(503).setBody("offline"));

        RSAPublicKey stale = keys.keyFor("known", "corr-stale").block();

        assertThat(stale).isEqualTo(cached);
        assertThat(authServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void unknownKidReloadsOnceThenUsesShortNegativeCache() {
        authServer.enqueue(jwksResponse(keyPair, "another-key"));

        assertThatThrownBy(() -> keys.keyFor("missing", "corr-1").block())
                .isInstanceOf(UnknownKeyException.class);
        assertThatThrownBy(() -> keys.keyFor("missing", "corr-2").block())
                .isInstanceOf(UnknownKeyException.class);

        assertThat(authServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void authFailureWithoutCachedKeyBecomesServiceUnavailable() {
        authServer.enqueue(new MockResponse().setResponseCode(503).setBody("offline"));

        assertThatThrownBy(() -> keys.keyFor("missing", "corr-1").block())
                .isInstanceOf(AuthServiceUnavailableException.class);
        assertThat(authServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsForSameUnknownKidUseOneNetworkRefresh() {
        authServer.enqueue(jwksResponse(keyPair, "shared-kid").setHeadersDelay(150, TimeUnit.MILLISECONDS));

        var publicKeys = Flux.range(0, 12)
                .flatMap(index -> keys.keyFor("shared-kid", "corr-" + index).subscribeOn(Schedulers.parallel()))
                .collectList().block();

        assertThat(publicKeys).hasSize(12).allMatch(key -> key.equals(publicKeys.get(0)));
        assertThat(authServer.getRequestCount()).isEqualTo(1);
    }

    private MockResponse jwksResponse(KeyPair pair, String kid) {
        try {
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                    .keyID(kid).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build();
            return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json")
                    .setBody(new JWKSet(jwk).toString());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) { this.current = new AtomicReference<>(initial); }
        private void advance(Duration duration) { current.updateAndGet(now -> now.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
    }
}
