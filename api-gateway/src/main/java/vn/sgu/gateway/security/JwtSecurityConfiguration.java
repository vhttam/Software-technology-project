package vn.sgu.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import vn.sgu.gateway.web.GatewayErrorWriter;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Configuration
public class JwtSecurityConfiguration {
    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${gateway.jwt.public-key-location}") Resource publicKeyResource,
            @Value("${gateway.jwt.jwk-set-uri:}") String jwkSetUri) throws Exception {
        NimbusReactiveJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusReactiveJwtDecoder.withPublicKey(readRsaPublicKey(publicKeyResource)).build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        return decoder;
    }

    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) {
                List<String> roles = jwt.getClaimAsStringList("roles");
                role = roles == null || roles.isEmpty() ? "" : roles.get(0);
            }
            List<SimpleGrantedAuthority> authorities = role.isBlank()
                    ? List.of() : List.of(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
            String principalName = jwt.getClaimAsString("userId");
            if (principalName == null || principalName.isBlank()) principalName = jwt.getSubject();
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, principalName));
        };
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> converter,
            GatewayErrorWriter errors) {
        ServerAuthenticationEntryPoint entryPoint = (exchange, ex) -> errors.write(exchange, 401,
                "UNAUTHORIZED", "Thiếu hoặc access token không hợp lệ/hết hạn");
        ServerAccessDeniedHandler deniedHandler = (exchange, ex) -> errors.write(exchange, 403,
                "FORBIDDEN", "Không đủ vai trò để thực hiện thao tác");

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/internal/jobs/progress").permitAll()
                        .pathMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/client/**").permitAll()
                        .pathMatchers("/api/v1/**").hasRole("CONTENT_ADMIN")
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private static RSAPublicKey readRsaPublicKey(Resource resource) throws Exception {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String base64 = pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
