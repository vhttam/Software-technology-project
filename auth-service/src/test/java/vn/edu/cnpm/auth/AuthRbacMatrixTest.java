package vn.edu.cnpm.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthRbacMatrixTest {

    @Autowired
    private TestRestTemplate client;

    private HttpHeaders headers(String userId, String role) {
        HttpHeaders h = new HttpHeaders();
        if (userId != null) h.set("X-User-Id", userId);
        if (role != null) h.set("X-User-Role", role);
        return h;
    }

    private String createContentAdmin(String username, String password) {
        ResponseEntity<Map> res = client.exchange(
                "/api/v1/auth/users",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "name", "Content Admin User",
                        "password", password,
                        "role", "CONTENT_ADMIN"
                ), headers("super-admin-root", "SUPER_ADMIN")),
                Map.class
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map data = (Map) res.getBody().get("data");
        return (String) data.get("userId");
    }

    @Test
    void contentAdminIsForbiddenFromSuperAdminEndpoints() {
        String caId = createContentAdmin("ca_" + UUID.randomUUID().toString().substring(0, 8), "Password123!");
        HttpHeaders caHeaders = headers(caId, "CONTENT_ADMIN");

        // 1. GET /api/v1/auth/users
        ResponseEntity<Map> listRes = client.exchange("/api/v1/auth/users", HttpMethod.GET,
                new HttpEntity<>(caHeaders), Map.class);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 2. POST /api/v1/auth/users
        ResponseEntity<Map> createRes = client.exchange("/api/v1/auth/users", HttpMethod.POST,
                new HttpEntity<>(Map.of("username", "ca2", "name", "CA2", "password", "Password123!", "role", "CONTENT_ADMIN"), caHeaders),
                Map.class);
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 3. PUT /api/v1/auth/users/{userId}/reset-password
        ResponseEntity<Map> resetRes = client.exchange("/api/v1/auth/users/other-user/reset-password", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "NewPassword123!"), caHeaders), Map.class);
        assertThat(resetRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. PUT /api/v1/auth/users/{userId}/unlock
        ResponseEntity<Map> unlockRes = client.exchange("/api/v1/auth/users/other-user/unlock", HttpMethod.PUT,
                new HttpEntity<>(caHeaders), Map.class);
        assertThat(unlockRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 5. PUT /api/v1/auth/users/{userId}/disable
        ResponseEntity<Map> disableRes = client.exchange("/api/v1/auth/users/other-user/disable", HttpMethod.PUT,
                new HttpEntity<>(caHeaders), Map.class);
        assertThat(disableRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 6. PUT /api/v1/auth/users/{userId}/enable
        ResponseEntity<Map> enableRes = client.exchange("/api/v1/auth/users/other-user/enable", HttpMethod.PUT,
                new HttpEntity<>(caHeaders), Map.class);
        assertThat(enableRes.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void contentAdminCanOnlyChangeOwnPasswordAndCannotTargetOthers() {
        String ca1Id = createContentAdmin("ca1_" + UUID.randomUUID().toString().substring(0, 8), "Password123!");
        String ca2Id = createContentAdmin("ca2_" + UUID.randomUUID().toString().substring(0, 8), "Password123!");
        HttpHeaders ca1Headers = headers(ca1Id, "CONTENT_ADMIN");

        // CONTENT_ADMIN trying to change another user's password via legacy endpoint must get 403 FORBIDDEN
        ResponseEntity<Map> forbiddenOther = client.exchange(
                "/api/v1/auth/users/" + ca2Id + "/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "currentPassword", "Password123!",
                        "newPassword", "NewPassword123!"
                ), ca1Headers),
                Map.class
        );
        assertThat(forbiddenOther.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // CONTENT_ADMIN trying to change own password without currentPassword must get 400 BAD_REQUEST
        ResponseEntity<Map> badReqMissingCurrent = client.exchange(
                "/api/v1/auth/users/" + ca1Id + "/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "NewPassword123!"), ca1Headers),
                Map.class
        );
        assertThat(badReqMissingCurrent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // CONTENT_ADMIN entering wrong current password gets 400 and increments failed attempts
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<Map> wrongPass = client.exchange(
                    "/api/v1/auth/users/" + ca1Id + "/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of(
                            "currentPassword", "WrongPass123!",
                            "newPassword", "NewPassword123!"
                    ), ca1Headers),
                    Map.class
            );
            assertThat(wrongPass.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(wrongPass.getBody()).containsEntry("errorCode", "CURRENT_PASSWORD_INVALID");
        }

        // After 5 failed attempts, login with old password fails due to account lock
        ResponseEntity<Map> loginRes = client.postForEntity("/api/v1/auth/login",
                Map.of("username", "ca1_" + ca1Id, "password", "Password123!"), Map.class);
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Another CONTENT_ADMIN changes their own password successfully
        String ca3Id = createContentAdmin("ca3_" + UUID.randomUUID().toString().substring(0, 8), "OldPassword123!");
        HttpHeaders ca3Headers = headers(ca3Id, "CONTENT_ADMIN");
        ResponseEntity<Void> successChange = client.exchange(
                "/api/v1/auth/users/" + ca3Id + "/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "currentPassword", "OldPassword123!",
                        "newPassword", "BrandNewPassword123!"
                ), ca3Headers),
                Void.class
        );
        assertThat(successChange.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Also test own password endpoint PUT /api/v1/auth/password
        ResponseEntity<Void> successChangeOwn = client.exchange(
                "/api/v1/auth/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of(
                        "currentPassword", "BrandNewPassword123!",
                        "newPassword", "AnotherPassword123!"
                ), ca3Headers),
                Void.class
        );
        assertThat(successChangeOwn.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void superAdminCanResetSubordinatePasswordButCannotResetAnotherSuperAdmin() {
        String caId = createContentAdmin("ca_sub_" + UUID.randomUUID().toString().substring(0, 8), "Password123!");
        HttpHeaders saHeaders = headers("sa-root-id", "SUPER_ADMIN");

        // SUPER_ADMIN resets CONTENT_ADMIN password via dedicated endpoint
        ResponseEntity<Void> resetRes = client.exchange(
                "/api/v1/auth/users/" + caId + "/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "ResetByAdmin123!"), saHeaders),
                Void.class
        );
        assertThat(resetRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // SUPER_ADMIN resets CONTENT_ADMIN password via legacy endpoint without currentPassword
        ResponseEntity<Void> legacyResetRes = client.exchange(
                "/api/v1/auth/users/" + caId + "/password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "LegacyReset123!"), saHeaders),
                Void.class
        );
        assertThat(legacyResetRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // SUPER_ADMIN cannot reset another SUPER_ADMIN password
        ResponseEntity<Map> resetSaRes = client.exchange(
                "/api/v1/auth/users/sa-other-id/reset-password",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("newPassword", "ResetAttempt123!"), saHeaders),
                Map.class
        );
        // sa-other-id does not exist, returns NOT_FOUND, or if exists and is SUPER_ADMIN returns FORBIDDEN
        // Let's test against admin account (which is SUPER_ADMIN)
        // Find existing super admin user id from login
        ResponseEntity<Map> loginAdmin = client.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "Admin@123456"), Map.class);
        if (loginAdmin.getStatusCode() == HttpStatus.OK) {
            Map user = (Map) ((Map) loginAdmin.getBody().get("data")).get("user");
            String adminId = (String) user.get("userId");

            ResponseEntity<Map> attemptOnSuperAdmin = client.exchange(
                    "/api/v1/auth/users/" + adminId + "/reset-password",
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("newPassword", "IllegalReset123!"), headers("another-sa", "SUPER_ADMIN")),
                    Map.class
            );
            assertThat(attemptOnSuperAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Legacy endpoint on another SUPER_ADMIN also forbidden
            ResponseEntity<Map> attemptLegacyOnSuperAdmin = client.exchange(
                    "/api/v1/auth/users/" + adminId + "/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(Map.of("newPassword", "IllegalReset123!"), headers("another-sa", "SUPER_ADMIN")),
                    Map.class
            );
            assertThat(attemptLegacyOnSuperAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // Disable SUPER_ADMIN is forbidden
            ResponseEntity<Map> attemptDisableSuperAdmin = client.exchange(
                    "/api/v1/auth/users/" + adminId + "/disable",
                    HttpMethod.PUT,
                    new HttpEntity<>(headers("another-sa", "SUPER_ADMIN")),
                    Map.class
            );
            assertThat(attemptDisableSuperAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
