package vn.edu.cnpm.auth.service;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.UUID;

@Configuration
public class BootstrapAdmin {
    @Bean
    ApplicationRunner seedAdmin(JdbcTemplate jdbc, PasswordEncoder encoder, Environment environment) {
        return args -> {
            String username = environment.getProperty("BOOTSTRAP_ADMIN_USERNAME", "");
            String name = environment.getProperty("BOOTSTRAP_ADMIN_NAME", "");
            String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD", "");
            if (username.isBlank() || name.isBlank() || password.length() < 8) return;
            boolean demoMode = "true".equalsIgnoreCase(environment.getProperty("DEMO_MODE", "false"));
            boolean forceReset = "true".equalsIgnoreCase(environment.getProperty("BOOTSTRAP_ADMIN_FORCE_RESET", "false"));

            var existing = jdbc.queryForList("SELECT user_id, password_hash, status FROM users WHERE username=?", username);
            if (existing.isEmpty()) {
                String now = Instant.now().toString();
                String role = "admin".equalsIgnoreCase(username) ? "SUPER_ADMIN" : environment.getProperty("BOOTSTRAP_ADMIN_ROLE", "SUPER_ADMIN");
                jdbc.update("INSERT INTO users(user_id,username,name,role,password_hash,failed_login_count,locked_until,created_at,updated_at,status) VALUES(?,?,?,?,?,0,NULL,?,?,'ACTIVE')",
                        UUID.randomUUID().toString(), username, name, role, encoder.encode(password), now, now);
            } else if (demoMode || forceReset) {
                String status = (String) existing.get(0).get("status");
                String now = Instant.now().toString();
                if (forceReset || !"DISABLED".equalsIgnoreCase(status)) {
                    String newStatus = forceReset ? "ACTIVE" : status;
                    jdbc.update("UPDATE users SET password_hash=?, failed_login_count=0, locked_until=NULL, status=?, updated_at=? WHERE username=?",
                            encoder.encode(password), newStatus, now, username);
                }
            }
        };
    }
}
