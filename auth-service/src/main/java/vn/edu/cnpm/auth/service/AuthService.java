package vn.edu.cnpm.auth.service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.cnpm.auth.api.ApiException;
import vn.edu.cnpm.auth.security.SigningKeys;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final Duration LOCK_LIFETIME = Duration.ofMinutes(15);
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final PasswordEncoder passwords;
    private final SigningKeys signingKeys;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc, DataSource dataSource, PasswordEncoder passwords, SigningKeys signingKeys, Clock clock) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.passwords = passwords;
        this.signingKeys = signingKeys;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> login(String username, String password) {
        if (username == null || username.isBlank() || password == null) throw invalidCredentials();
        Map<String, Object> user = findForUpdate(username);
        if (user == null) throw invalidCredentials();

        String status = String.valueOf(user.getOrDefault("status", "ACTIVE"));
        if ("DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "Tài khoản này đã bị vô hiệu hóa bởi Quản trị viên cấp cao.");
        }

        Instant now = clock.instant();
        Instant lockedUntil = parseInstant((String) user.get("locked_until"));
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            long remainingMinutes = Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                    "Tài khoản đang bị khóa tạm thời. Vui lòng thử lại sau khoảng " + remainingMinutes + " phút hoặc liên hệ quản trị viên.");
        }

        if (lockedUntil != null) {
            jdbc.update("UPDATE users SET failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?",
                    now.toString(), user.get("user_id"));
        }

        if (!passwords.matches(password, (String) user.get("password_hash"))) {
            int currentFailed = user.get("failed_login_count") instanceof Number n ? n.intValue() : 0;
            boolean willLock = currentFailed >= 4;
            jdbc.update("""
                    UPDATE users
                       SET failed_login_count = CASE WHEN failed_login_count >= 4 THEN 5 ELSE failed_login_count + 1 END,
                           locked_until = CASE WHEN failed_login_count >= 4 THEN ? ELSE locked_until END,
                           updated_at = ?
                     WHERE user_id = ?
                    """, willLock ? now.plus(LOCK_LIFETIME).toString() : null, now.toString(), user.get("user_id"));

            if (willLock) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "ACCOUNT_LOCKED",
                        "Tài khoản đã bị khóa tạm thời 15 phút do nhập sai mật khẩu quá 5 lần.");
            }
            throw invalidCredentials();
        }

        jdbc.update("UPDATE users SET failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?", now.toString(), user.get("user_id"));
        String refreshToken = opaqueToken();
        jdbc.update("INSERT INTO refresh_tokens(token_id,user_id,token_hash,expires_at,revoked,created_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), user.get("user_id"), sha256(refreshToken), now.plus(REFRESH_LIFETIME).toString(), false, now.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", issueAccessToken(user));
        result.put("refreshToken", refreshToken);
        result.put("tokenType", "Bearer");
        result.put("expiresIn", ACCESS_LIFETIME.toSeconds());
        result.put("refreshExpiresIn", REFRESH_LIFETIME.toSeconds());
        result.put("user", publicUser(user));
        return result;
    }

    public Object jwks() { return signingKeys.jwks(); }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> refresh(String token) {
        if (token == null || token.isBlank()) throw invalidRefresh();
        String hash = sha256(token);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.user_id AS user_id,u.username AS username,u.name AS name,u.role AS role,u.status AS status
                  FROM refresh_tokens r JOIN users u ON u.user_id=r.user_id
                 WHERE r.token_hash=? AND r.revoked=0
                """, hash);
        if (rows.isEmpty()) throw invalidRefresh();
        Map<String, Object> user = rows.get(0);
        if ("DISABLED".equals(String.valueOf(user.get("status")))) {
            jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE token_hash=?", hash);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa.");
        }
        String expiration = jdbc.queryForObject("SELECT expires_at FROM refresh_tokens WHERE token_hash=?", String.class, hash);
        Instant expires = parseInstant(expiration);
        if (expires == null || !expires.isAfter(clock.instant())) {
            jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE token_hash=?", hash);
            throw invalidRefresh();
        }
        return Map.of("accessToken", issueAccessToken(user), "tokenType", "Bearer", "expiresIn", ACCESS_LIFETIME.toSeconds());
    }

    @Transactional
    public void logout(String token, String userId) {
        if (token == null || token.isBlank() || userId == null || userId.isBlank()) return;
        jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE token_hash=? AND user_id=?", sha256(token), userId);
    }

    public Map<String, Object> listUsers(int page, int pageSize, String query, String role, String status, Boolean locked, String actorRole) {
        requireSuperAdminRole(actorRole);
        validatePage(page, pageSize);
        StringBuilder sql = new StringBuilder("SELECT user_id,username,name,role,failed_login_count,locked_until,status,created_at,updated_at FROM users WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql.append(" AND role = ?");
            args.add(role.trim());
        }
        if (Boolean.TRUE.equals(locked)) {
            sql.append(" AND locked_until > ?");
            args.add(clock.instant().toString());
        } else if (Boolean.FALSE.equals(locked)) {
            sql.append(" AND (locked_until IS NULL OR locked_until <= ?)");
            args.add(clock.instant().toString());
        }
        sql.append(" ORDER BY username");
        List<Map<String, Object>> rawRows = jdbc.queryForList(sql.toString(), args.toArray());
        List<Map<String, Object>> all = rawRows.stream().map(this::camelizeUser).toList();

        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            all = all.stream().filter(u -> {
                String userStatus = String.valueOf(u.get("status"));
                if ("LOCKED".equals(s) || "LOCKED_TEMP".equals(s)) {
                    return "LOCKED_TEMP".equals(userStatus) || "LOCKED".equals(userStatus);
                }
                return s.equals(userStatus);
            }).toList();
        }

        if (query != null && !query.isBlank()) {
            String q = unaccent(query);
            all = all.stream().filter(u ->
                unaccent((String) u.get("name")).contains(q) ||
                unaccent((String) u.get("username")).contains(q)
            ).toList();
        }

        int start = Math.min((page - 1) * pageSize, all.size());
        int end = Math.min(start + pageSize, all.size());
        return Map.of("items", all.subList(start, end),
                "page", page, "pageSize", pageSize, "totalItems", all.size(),
                "totalPages", all.isEmpty() ? 0 : (all.size() + pageSize - 1) / pageSize);
    }

    public Map<String, Object> listUsers(int page, int pageSize, String query, Boolean locked, String actorRole) {
        return listUsers(page, pageSize, query, null, null, locked, actorRole);
    }

    @Transactional
    public Map<String, Object> createUser(String username, String name, String password, String role, String actorId, String actorRole) {
        requireSuperAdminRole(actorRole);
        if (username == null || !username.matches("[A-Za-z0-9._-]{3,80}") ||
                name == null || name.isBlank() || role == null || !role.equals("CONTENT_ADMIN") ||
                password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu tài khoản không hợp lệ. Chỉ được phép tạo tài khoản có vai trò CONTENT_ADMIN, mật khẩu tối thiểu 8 ký tự.",
                    Map.of("username", "required/format", "name", "required", "role", "CONTENT_ADMIN", "password", "min 8"));
        }
        if (!jdbc.queryForList("SELECT user_id FROM users WHERE username=?", username).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Tên đăng nhập đã tồn tại");
        }
        Instant now = clock.instant();
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO users(user_id,username,name,role,password_hash,failed_login_count,locked_until,created_at,updated_at,status) VALUES(?,?,?,?,?,0,NULL,?,?,'ACTIVE')",
                    id, username, name, role, passwords.encode(password), now.toString(), now.toString());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "Tên đăng nhập đã tồn tại");
        }
        recordAudit("CREATE_USER", actorId, id, "Tạo tài khoản quản trị nội dung " + username);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("userId", id); user.put("username", username); user.put("name", name); user.put("role", role);
        user.put("status", "ACTIVE");
        user.put("failedLoginCount", 0); user.put("lockedUntil", null); user.put("createdAt", now.toString()); user.put("updatedAt", now.toString());
        return user;
    }

    @Transactional
    public void changeOwnPassword(String actorId, String currentPassword, String newPassword) {
        if (actorId == null || actorId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu thông tin người dùng.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu mới phải có ít nhất 8 ký tự.");
        }
        Map<String, Object> user = findById(actorId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");

        if (currentPassword == null || !passwords.matches(currentPassword, (String) user.get("password_hash"))) {
            Instant now = clock.instant();
            jdbc.update("""
                    UPDATE users
                       SET failed_login_count = CASE WHEN failed_login_count >= 4 THEN 5 ELSE failed_login_count + 1 END,
                           locked_until = CASE WHEN failed_login_count >= 4 THEN ? ELSE locked_until END,
                           updated_at = ?
                     WHERE user_id = ?
                    """, now.plus(LOCK_LIFETIME).toString(), now.toString(), actorId);
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INVALID", "Mật khẩu hiện tại không đúng.");
        }

        if (passwords.matches(newPassword, (String) user.get("password_hash"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NEW_PASSWORD_SAME", "Mật khẩu mới phải khác mật khẩu hiện tại.");
        }

        Instant now = clock.instant();
        jdbc.update("UPDATE users SET password_hash=?, failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?",
                passwords.encode(newPassword), now.toString(), actorId);
        jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE user_id=?", actorId);
        recordAudit("CHANGE_OWN_PASSWORD", actorId, actorId, "Người dùng tự đổi mật khẩu cá nhân thành công.");
    }

    @Transactional
    public void resetSubordinatePassword(String targetUserId, String actorId, String actorRole, String newPassword) {
        requireSuperAdminRole(actorRole);
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mã tài khoản không hợp lệ.");
        }
        if (targetUserId.equals(actorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_RESET_SELF",
                    "Không thể tự đặt lại mật khẩu của chính mình qua tính năng này. Vui lòng dùng chức năng Đổi mật khẩu cá nhân.");
        }
        Map<String, Object> targetUser = findById(targetUserId);
        if (targetUser == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");

        String targetRole = String.valueOf(targetUser.get("role"));
        if ("SUPER_ADMIN".equals(targetRole)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Không thể đặt lại mật khẩu cho tài khoản có vai trò ngang cấp hoặc cao hơn.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mật khẩu mới phải có ít nhất 8 ký tự.");
        }

        Instant now = clock.instant();
        jdbc.update("UPDATE users SET password_hash=?, failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?",
                passwords.encode(newPassword), now.toString(), targetUserId);
        jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE user_id=?", targetUserId);
        recordAudit("RESET_SUBORDINATE_PASSWORD", actorId, targetUserId,
                "Quản trị viên cấp cao (" + actorId + ") đặt lại mật khẩu cho tài khoản (" + targetUserId + ")");
    }

    @Transactional
    public Map<String, Object> unlock(String targetUserId, String actorId, String actorRole) {
        requireSuperAdminRole(actorRole);
        Map<String, Object> user = findById(targetUserId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản");

        if ("SUPER_ADMIN".equals(String.valueOf(user.get("role")))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không thể thao tác trên tài khoản có vai trò ngang cấp hoặc cao hơn.");
        }

        Instant lockedUntil = parseInstant((String) user.get("locked_until"));
        int failedCount = user.get("failed_login_count") instanceof Number n ? n.intValue() : 0;
        boolean isLocked = (lockedUntil != null && lockedUntil.isAfter(clock.instant())) || failedCount > 0;
        if (!isLocked) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_NOT_LOCKED", "Tài khoản hiện không bị khóa.");
        }

        Instant now = clock.instant();
        jdbc.update("UPDATE users SET failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?",
                now.toString(), targetUserId);
        recordAudit("UNLOCK_ACCOUNT", actorId, targetUserId, "Quản trị viên cấp cao mở khóa tài khoản.");
        return Map.of("userId", targetUserId, "locked", false);
    }

    @Transactional
    public void disableUser(String targetUserId, String actorId, String actorRole) {
        requireSuperAdminRole(actorRole);
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Mã tài khoản không hợp lệ.");
        }
        if (targetUserId.equals(actorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_DISABLE_SELF", "Không thể tự vô hiệu hóa tài khoản của chính mình.");
        }
        Map<String, Object> user = findById(targetUserId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");

        if ("SUPER_ADMIN".equals(String.valueOf(user.get("role")))) {
            Integer activeSuperAdmins = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE role='SUPER_ADMIN' AND status='ACTIVE'", Integer.class);
            if (activeSuperAdmins == null || activeSuperAdmins <= 1) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Không thể vô hiệu hóa tài khoản SUPER_ADMIN đang hoạt động cuối cùng của hệ thống.");
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Không thể vô hiệu hóa tài khoản có vai trò ngang cấp hoặc cao hơn.");
        }

        Instant now = clock.instant();
        jdbc.update("UPDATE users SET status='DISABLED', updated_at=? WHERE user_id=?", now.toString(), targetUserId);
        jdbc.update("UPDATE refresh_tokens SET revoked=1 WHERE user_id=?", targetUserId);
        recordAudit("DISABLE_ACCOUNT", actorId, targetUserId, "Quản trị viên cấp cao vô hiệu hóa tài khoản.");
    }

    @Transactional
    public void enableUser(String targetUserId, String actorId, String actorRole) {
        requireSuperAdminRole(actorRole);
        Map<String, Object> user = findById(targetUserId);
        if (user == null) throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản.");

        Instant now = clock.instant();
        jdbc.update("UPDATE users SET status='ACTIVE', failed_login_count=0, locked_until=NULL, updated_at=? WHERE user_id=?",
                now.toString(), targetUserId);
        recordAudit("ENABLE_ACCOUNT", actorId, targetUserId, "Quản trị viên cấp cao kích hoạt lại tài khoản.");
    }

    private void recordAudit(String action, String actorId, String targetId, String details) {
        try {
            jdbc.update("INSERT INTO audit_logs(log_id,action,actor_id,target_id,details,created_at) VALUES(?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), action, actorId == null ? "SYSTEM" : actorId,
                    targetId == null ? "SYSTEM" : targetId, details, clock.instant().toString());
        } catch (Exception ignored) {
        }
    }

    private void requireSuperAdminRole(String role) {
        if (!"SUPER_ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Chỉ Quản trị viên cấp cao (SUPER_ADMIN) mới có quyền quản lý tài khoản.");
        }
    }

    private Map<String, Object> findForUpdate(String username) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            boolean sqlServer = connection.getMetaData().getDatabaseProductName().toLowerCase().contains("microsoft");
            String sql = sqlServer
                    ? "SELECT user_id,username,name,role,password_hash,failed_login_count,locked_until,status FROM users WITH (UPDLOCK, ROWLOCK) WHERE username=?"
                    : "SELECT user_id,username,name,role,password_hash,failed_login_count,locked_until,status FROM users WHERE username=? FOR UPDATE";
            return jdbc.query(sql, rs -> {
                if (!rs.next()) return null;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("user_id", rs.getString("user_id")); row.put("username", rs.getString("username"));
                row.put("name", rs.getString("name")); row.put("role", rs.getString("role"));
                row.put("password_hash", rs.getString("password_hash")); row.put("failed_login_count", rs.getInt("failed_login_count"));
                row.put("locked_until", rs.getString("locked_until"));
                row.put("status", rs.getString("status"));
                return row;
            }, username);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private Map<String, Object> findById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM users WHERE user_id=?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> publicUser(Map<String, Object> user) {
        return Map.of("userId", user.get("user_id"), "username", user.get("username"),
                "name", user.get("name"), "role", user.get("role"));
    }

    private Map<String, Object> camelizeUser(Map<String, Object> user) {
        Instant now = clock.instant();
        Instant lockedUntil = parseInstant((String) user.get("locked_until"));
        String rawStatus = String.valueOf(user.getOrDefault("status", "ACTIVE"));
        String effectiveStatus = "DISABLED".equals(rawStatus)
                ? "DISABLED"
                : (lockedUntil != null && lockedUntil.isAfter(now) ? "LOCKED_TEMP" : "ACTIVE");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.get("user_id")); result.put("username", user.get("username"));
        result.put("name", user.get("name")); result.put("role", user.get("role"));
        result.put("status", effectiveStatus);
        result.put("failedLoginCount", user.get("failed_login_count")); result.put("lockedUntil", user.get("locked_until"));
        result.put("createdAt", user.get("created_at")); result.put("updatedAt", user.get("updated_at"));
        return result;
    }

    private String issueAccessToken(Map<String, Object> user) {
        Instant now = clock.instant();
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(String.valueOf(user.get("user_id")))
                    .claim("role", String.valueOf(user.get("role"))).issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ACCESS_LIFETIME))).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(signingKeys.keyId()).type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(signingKeys.privateKey()));
            return jwt.serialize();
        } catch (Exception ex) { throw new IllegalStateException("Không thể ký access token", ex); }
    }

    private String opaqueToken() {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private Instant parseInstant(String text) { return text == null ? null : Instant.parse(text); }
    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Thông tin đăng nhập không đúng hoặc tài khoản tạm thời bị khóa");
    }
    private ApiException invalidRefresh() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ, hết hạn hoặc đã bị thu hồi");
    }
    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Tham số phân trang không hợp lệ");
    }
    private static final java.util.regex.Pattern DIACRITICS = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static String unaccent(String text) {
        if (text == null) return "";
        String normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(normalized).replaceAll("");
        return withoutDiacritics.replace('đ', 'd').replace('Đ', 'D').toLowerCase().trim();
    }
}
