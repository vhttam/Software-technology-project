package vn.edu.cnpm.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthContractTest {
    @Autowired TestRestTemplate client;
    @Autowired ObjectMapper mapper;

    @Test
    void loginJwksRefreshAndLogoutFollowContract() throws Exception {
        String username="auth_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        createUser(username,"SecurePassword1!");
        ResponseEntity<Map> login=client.postForEntity("/api/v1/auth/login",Map.of("username",username,"password","SecurePassword1!"),Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map data=(Map)login.getBody().get("data");
        String access=(String)data.get("accessToken"),refresh=(String)data.get("refreshToken");
        assertThat(data.get("expiresIn")).isEqualTo(900);
        assertThat(data.get("refreshExpiresIn")).isEqualTo(604800);
        assertThat(data.get("user")).asString().doesNotContain("password");

        Map jwks=client.getForObject("/api/v1/auth/.well-known/jwks.json",Map.class);
        assertThat(jwks).containsKey("keys");
        RSAKey publicKey=JWKSet.parse(mapper.writeValueAsString(jwks)).getKeys().get(0).toRSAKey();
        SignedJWT parsed=SignedJWT.parse(access);
        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo(publicKey.getKeyID());
        assertThat(parsed.verify(new RSASSAVerifier(publicKey))).isTrue();
        assertThat(parsed.getJWTClaimsSet().getSubject()).isNotBlank();
        assertThat(parsed.getJWTClaimsSet().getClaim("userId")).isNull();
        assertThat(parsed.getJWTClaimsSet().getExpirationTime().toInstant()).isAfter(Instant.now());

        ResponseEntity<Map> refreshed=client.postForEntity("/api/v1/auth/refresh",Map.of("refreshToken",refresh),Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().get("data")).asString().contains("accessToken");

        HttpHeaders headers=new HttpHeaders();headers.set("X-User-Id",(String)((Map)data.get("user")).get("userId"));
        ResponseEntity<Void> logout=client.exchange("/api/v1/auth/logout",HttpMethod.POST,new HttpEntity<>(Map.of("refreshToken",refresh),headers),Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> afterLogout=client.postForEntity("/api/v1/auth/refresh",Map.of("refreshToken",refresh),Map.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(afterLogout.getBody()).containsEntry("errorCode","INVALID_REFRESH_TOKEN");
    }

    @Test
    void internalAccountApiHidesSecretsAndReturnsStableErrors() {
        String username="user_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        ResponseEntity<Map> created=createUser(username,"SecurePassword2!");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map data=(Map)created.getBody().get("data");
        assertThat(data.keySet()).doesNotContain("passwordHash","refreshToken");

        ResponseEntity<Map> duplicate=createUser(username,"SecurePassword2!");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("errorCode","USERNAME_EXISTS");

        ResponseEntity<Map> forbidden=client.exchange("/api/v1/auth/users?page=1&pageSize=20",HttpMethod.GET,
                new HttpEntity<>(headers("VISITOR")),Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> invalid=client.postForEntity("/api/v1/auth/login",Map.of("username",username,"password","wrong"),Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalid.getBody()).containsEntry("errorCode","INVALID_CREDENTIALS");
        assertThat(invalid.getBody().get("status")).isEqualTo("error");
        assertThat(invalid.getBody()).containsKey("correlationId");
    }

    private ResponseEntity<Map> createUser(String username,String password) {
        return client.exchange("/api/v1/auth/users",HttpMethod.POST,
                new HttpEntity<>(Map.of("username",username,"name","Test Admin","password",password,"role","CONTENT_ADMIN"),
                        headers("SUPER_ADMIN")),Map.class);
    }
    private HttpHeaders headers(String role){HttpHeaders h=new HttpHeaders();h.set("X-User-Role",role);h.set("X-User-Id","test-super-admin");return h;}
}
