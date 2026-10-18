package vn.edu.cnpm.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.cnpm.auth.security.SigningKeys;

import java.nio.file.Files;
import java.nio.file.Path;

class SigningKeysPersistenceTest {
    @TempDir
    Path tempDir;

    @Test
    void localSigningKeyAndKidRemainStableAcrossRestarts() {
        Path keyPath = tempDir.resolve("auth-signing-key.pk8");
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_PRIVATE_KEY_PATH", keyPath.toString());

        SigningKeys first = new SigningKeys(environment);
        SigningKeys restarted = new SigningKeys(environment);

        assertThat(Files.exists(keyPath)).isTrue();
        assertThat(first.keyId()).isEqualTo(restarted.keyId());
        assertThat(first.jwks()).isEqualTo(restarted.jwks());
    }
}
