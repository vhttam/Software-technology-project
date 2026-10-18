package vn.edu.cnpm.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthConcurrencyContractTest {
    @Autowired TestRestTemplate client;
    @Autowired JdbcTemplate jdbc;

    @Test
    void concurrentWrongPasswordsAtomicallyLockAtFiveAttempts() throws Exception {
        String username="lock_"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        HttpHeaders admin=new HttpHeaders();admin.set("X-User-Role","SUPER_ADMIN");admin.set("X-User-Id","test-super-admin");
        client.exchange("/api/v1/auth/users",HttpMethod.POST,
                new HttpEntity<>(Map.of("username",username,"name","Lock Test","password","SecurePassword3!","role","CONTENT_ADMIN"),admin),Map.class);

        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> attempts=new ArrayList<>();
            for(int i=0;i<8;i++)attempts.add(()->client.postForEntity("/api/v1/auth/login",
                    Map.of("username",username,"password","incorrect"),Map.class).getStatusCode().value());
            for(Future<Integer> result:pool.invokeAll(attempts))assertThat(result.get()).isEqualTo(401);
        } finally {pool.shutdownNow();}

        Map<String,Object> user=jdbc.queryForMap("SELECT failed_login_count,locked_until FROM users WHERE username=?",username);
        assertThat(((Number)user.get("failed_login_count")).intValue()).isEqualTo(5);
        assertThat(user.get("locked_until")).isNotNull();
        ResponseEntity<Map> correct=client.postForEntity("/api/v1/auth/login",
                Map.of("username",username,"password","SecurePassword3!"),Map.class);
        assertThat(correct.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String userId=jdbc.queryForObject("SELECT user_id FROM users WHERE username=?",String.class,username);
        ResponseEntity<Map> unlocked=client.exchange("/api/v1/auth/users/{id}/unlock",HttpMethod.PUT,
                new HttpEntity<>(Map.of(),admin),Map.class,userId);
        assertThat(unlocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unlocked.getBody().get("data")).isEqualTo(Map.of("userId",userId,"locked",false));
        assertThat(client.postForEntity("/api/v1/auth/login",
                Map.of("username",username,"password","SecurePassword3!"),Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
