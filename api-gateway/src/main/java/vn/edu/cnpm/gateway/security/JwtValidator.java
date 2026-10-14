package vn.edu.cnpm.gateway.security;

import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import vn.edu.cnpm.gateway.config.GatewayProperties;
import vn.edu.cnpm.gateway.error.InvalidTokenException;

@Service
public class JwtValidator {
    private final JwksKeyService keys;
    private final Duration clockSkew;

    public JwtValidator(JwksKeyService keys, GatewayProperties properties) {
        this.keys = keys;
        this.clockSkew = properties.getJwtClockSkew();
    }

    public Mono<AuthenticatedUser> validate(String token, String correlationId) {
        final SignedJWT jwt;
        final String kid;
        try {
            jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                return Mono.error(new InvalidTokenException("JWT algorithm must be RS256"));
            }
            kid = jwt.getHeader().getKeyID();
            if (kid == null || kid.isBlank()) {
                return Mono.error(new InvalidTokenException("JWT kid is required"));
            }
        } catch (Exception exception) {
            return Mono.error(new InvalidTokenException("JWT could not be parsed", exception));
        }

        return keys.keyFor(kid, correlationId).flatMap(publicKey -> validateClaims(jwt, publicKey));
    }

    private Mono<AuthenticatedUser> validateClaims(SignedJWT jwt, RSAPublicKey publicKey) {
        try {
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                return Mono.error(new InvalidTokenException("JWT signature is invalid"));
            }
            var claims = jwt.getJWTClaimsSet();
            String subject = claims.getSubject();
            String role = claims.getStringClaim("role");
            Date issuedAt = claims.getIssueTime();
            Date expiresAt = claims.getExpirationTime();
            if (subject == null || subject.isBlank() || role == null || role.isBlank()
                    || issuedAt == null || expiresAt == null || containsControlCharacter(subject)
                    || containsControlCharacter(role)) {
                return Mono.error(new InvalidTokenException("JWT is missing a required claim"));
            }

            Instant now = Instant.now();
            if (now.isAfter(expiresAt.toInstant().plus(clockSkew))) {
                return Mono.error(new InvalidTokenException("JWT has expired"));
            }
            if (issuedAt.toInstant().isAfter(now.plus(clockSkew))) {
                return Mono.error(new InvalidTokenException("JWT issue time is in the future"));
            }
            return Mono.just(new AuthenticatedUser(subject, role));
        } catch (Exception exception) {
            return Mono.error(exception instanceof InvalidTokenException ? exception
                    : new InvalidTokenException("JWT validation failed", exception));
        }
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }
}
