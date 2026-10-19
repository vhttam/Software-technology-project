package vn.edu.cnpm.gateway.security;

import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.config.GatewayProperties;
import vn.edu.cnpm.gateway.error.AuthServiceUnavailableException;
import vn.edu.cnpm.gateway.error.UnknownKeyException;

@Service
public class JwksKeyService {
    private static final Logger log = LoggerFactory.getLogger(JwksKeyService.class);
    private static final String JWKS_PATH = "/api/v1/auth/.well-known/jwks.json";

    private final WebClient webClient;
    private final GatewayProperties properties;
    private final Clock clock;
    private final ConcurrentMap<String, CachedKey> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> negativeCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Mono<RSAPublicKey>> inFlight = new ConcurrentHashMap<>();

    @Autowired
    public JwksKeyService(@Qualifier("jwksWebClient") WebClient jwksWebClient, GatewayProperties properties) {
        this(jwksWebClient, properties, Clock.systemUTC());
    }

    public JwksKeyService(WebClient jwksWebClient, GatewayProperties properties, Clock clock) {
        this.webClient = jwksWebClient;
        this.properties = properties;
        this.clock = clock;
    }

    public Mono<RSAPublicKey> keyFor(String kid, String correlationId) {
        Instant now = clock.instant();
        CachedKey cached = cache.get(kid);
        if (cached != null && now.isBefore(cached.expiresAt())) return Mono.just(cached.publicKey());

        Instant negativeUntil = negativeCache.get(kid);
        if (negativeUntil != null && now.isBefore(negativeUntil)) {
            return Mono.error(new UnknownKeyException(kid));
        }
        if (negativeUntil != null) negativeCache.remove(kid, negativeUntil);

        Mono<RSAPublicKey> reload = Mono.defer(() -> fetchAndCache(correlationId)
                        .flatMap(keys -> {
                            RSAPublicKey key = keys.get(kid);
                            if (key == null) {
                                negativeCache.put(kid, clock.instant().plus(properties.getUnknownKidTtl()));
                                return Mono.error(new UnknownKeyException(kid));
                            }
                            return Mono.just(key);
                        })
                        .onErrorResume(exception -> {
                            if (exception instanceof UnknownKeyException) return Mono.error(exception);
                            CachedKey stale = cache.get(kid);
                            log.warn("JWKS refresh failed correlationId={} cachedKeyAvailable={}",
                                    correlationId, stale != null);
                            if (stale != null) {
                                return Mono.just(stale.publicKey());
                            }
                            return Mono.error(new AuthServiceUnavailableException(
                                    "JWKS is unavailable and no cached key exists", exception));
                        }))
                .cache();
        Mono<RSAPublicKey> shared = inFlight.putIfAbsent(kid, reload);
        Mono<RSAPublicKey> selected = shared == null ? reload : shared;
        return selected.doFinally(signal -> inFlight.remove(kid, reload));
    }

    private Mono<Map<String, RSAPublicKey>> fetchAndCache(String correlationId) {
        return webClient.get().uri(JWKS_PATH).retrieve().bodyToMono(String.class)
                .map(body -> parseAndCache(body, correlationId))
                .onErrorMap(exception -> exception instanceof AuthServiceUnavailableException
                        ? exception : new AuthServiceUnavailableException("Unable to load JWKS", exception));
    }

    private Map<String, RSAPublicKey> parseAndCache(String responseBody, String correlationId) {
        final JWKSet jwkSet;
        try {
            jwkSet = JWKSet.parse(responseBody);
        } catch (ParseException exception) {
            throw new IllegalArgumentException("Auth Service returned an invalid JWKS document", exception);
        }

        Instant expiresAt = clock.instant().plus(properties.getJwksTtl());
        Map<String, RSAPublicKey> loaded = new HashMap<>();
        for (JWK jwk : jwkSet.getKeys()) {
            if (!(jwk instanceof RSAKey rsaKey) || rsaKey.getKeyID() == null) continue;
            if (rsaKey.getAlgorithm() != null && !JWSAlgorithm.RS256.equals(rsaKey.getAlgorithm())) continue;
            if (rsaKey.getKeyUse() != null && !com.nimbusds.jose.jwk.KeyUse.SIGNATURE.equals(rsaKey.getKeyUse())) continue;
            try {
                RSAPublicKey key = rsaKey.toRSAPublicKey();
                cache.put(rsaKey.getKeyID(), new CachedKey(key, expiresAt));
                negativeCache.remove(rsaKey.getKeyID());
                loaded.put(rsaKey.getKeyID(), key);
            } catch (JOSEException exception) {
                log.warn("Ignoring unusable RSA public key kid=redacted correlationId={}", correlationId);
            }
        }
        return loaded;
    }

    private record CachedKey(RSAPublicKey publicKey, Instant expiresAt) { }
}
