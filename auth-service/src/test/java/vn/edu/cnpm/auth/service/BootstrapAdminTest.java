package vn.edu.cnpm.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootstrapAdminTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder encoder;

    private BootstrapAdmin bootstrapAdmin;

    @BeforeEach
    void setUp() {
        bootstrapAdmin = new BootstrapAdmin();
    }

    @Test
    void testNormalModeRetainsExistingHashAndLockCounter() throws Exception {
        String username = "admin_norm_" + UUID.randomUUID().toString().substring(0, 8);
        String originalPassword = "OriginalPass123!";
        String originalHash = encoder.encode(originalPassword);
        String now = Instant.now().toString();

        jdbc.update("INSERT INTO users(user_id, username, name, role, password_hash, failed_login_count, locked_until, created_at, updated_at, status) VALUES(?,?,?,?,?,3,?,?,?,'ACTIVE')",
                UUID.randomUUID().toString(), username, "Admin Normal", "SUPER_ADMIN", originalHash, now, now, now);

        MockEnvironment env = new MockEnvironment();
        env.setProperty("BOOTSTRAP_ADMIN_USERNAME", username);
        env.setProperty("BOOTSTRAP_ADMIN_NAME", "Admin Normal");
        env.setProperty("BOOTSTRAP_ADMIN_PASSWORD", "NewEnvPassword123!");
        env.setProperty("DEMO_MODE", "false");
        env.setProperty("BOOTSTRAP_ADMIN_FORCE_RESET", "false");

        bootstrapAdmin.seedAdmin(jdbc, encoder, env).run(null);

        Map<String, Object> user = jdbc.queryForMap("SELECT password_hash, failed_login_count, locked_until FROM users WHERE username=?", username);
        assertThat(user.get("password_hash")).isEqualTo(originalHash);
        assertThat(((Number) user.get("failed_login_count")).intValue()).isEqualTo(3);
        assertThat(user.get("locked_until")).isEqualTo(now);
    }

    @Test
    void testDemoModeUpdatesHashAndResetsCounter() throws Exception {
        String username = "admin_demo_" + UUID.randomUUID().toString().substring(0, 8);
        String oldHash = encoder.encode("OldPassword123!");
        String now = Instant.now().toString();

        jdbc.update("INSERT INTO users(user_id, username, name, role, password_hash, failed_login_count, locked_until, created_at, updated_at, status) VALUES(?,?,?,?,?,4,?,?,?,'ACTIVE')",
                UUID.randomUUID().toString(), username, "Admin Demo", "SUPER_ADMIN", oldHash, now, now, now);

        MockEnvironment env = new MockEnvironment();
        env.setProperty("BOOTSTRAP_ADMIN_USERNAME", username);
        env.setProperty("BOOTSTRAP_ADMIN_NAME", "Admin Demo");
        String demoPass = "DemoSyncPassword123!";
        env.setProperty("BOOTSTRAP_ADMIN_PASSWORD", demoPass);
        env.setProperty("DEMO_MODE", "true");

        bootstrapAdmin.seedAdmin(jdbc, encoder, env).run(null);

        Map<String, Object> user = jdbc.queryForMap("SELECT password_hash, failed_login_count, locked_until FROM users WHERE username=?", username);
        String newHash = (String) user.get("password_hash");
        assertThat(encoder.matches(demoPass, newHash)).isTrue();
        assertThat(((Number) user.get("failed_login_count")).intValue()).isEqualTo(0);
        assertThat(user.get("locked_until")).isNull();
    }

    @Test
    void testFirstTimeCreationInsertsAdmin() throws Exception {
        String username = "admin_fresh_" + UUID.randomUUID().toString().substring(0, 8);
        String freshPass = "FreshAdminPassword123!";

        MockEnvironment env = new MockEnvironment();
        env.setProperty("BOOTSTRAP_ADMIN_USERNAME", username);
        env.setProperty("BOOTSTRAP_ADMIN_NAME", "Fresh Administrator");
        env.setProperty("BOOTSTRAP_ADMIN_PASSWORD", freshPass);
        env.setProperty("DEMO_MODE", "false");

        bootstrapAdmin.seedAdmin(jdbc, encoder, env).run(null);

        Map<String, Object> user = jdbc.queryForMap("SELECT username, name, role, password_hash, status FROM users WHERE username=?", username);
        assertThat(user.get("username")).isEqualTo(username);
        assertThat(user.get("name")).isEqualTo("Fresh Administrator");
        assertThat(user.get("role")).isEqualTo("SUPER_ADMIN");
        assertThat(encoder.matches(freshPass, (String) user.get("password_hash"))).isTrue();
        assertThat(user.get("status")).isEqualTo("ACTIVE");
    }
}
