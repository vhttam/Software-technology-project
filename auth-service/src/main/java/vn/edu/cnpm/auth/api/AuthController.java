package vn.edu.cnpm.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.cnpm.auth.service.AuthService;
import java.util.Map;

@RestController
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/api/v1/auth/login")
    public ApiEnvelope<Map<String,Object>> login(@Valid @RequestBody LoginRequest request) {
        return ApiEnvelope.success(auth.login(request.username(), request.password()));
    }

    @GetMapping("/api/v1/auth/.well-known/jwks.json")
    public Object jwks() { return auth.jwks(); }

    @PostMapping("/api/v1/auth/refresh")
    public ApiEnvelope<Map<String,Object>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiEnvelope.success(auth.refresh(request.refreshToken()));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        auth.logout(request.refreshToken(), http.getHeader("X-User-Id"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/auth/users")
    public ApiEnvelope<Map<String,Object>> users(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) String query,
            @RequestParam(required=false) String role, @RequestParam(required=false) String status,
            @RequestParam(required=false) Boolean locked, HttpServletRequest request) {
        return ApiEnvelope.success(auth.listUsers(page, pageSize, query, role, status, locked, request.getHeader("X-User-Role")));
    }

    @PostMapping("/api/v1/auth/users")
    public ResponseEntity<ApiEnvelope<Map<String,Object>>> create(@Valid @RequestBody CreateUserRequest body, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.success(
                auth.createUser(body.username(), body.name(), body.password(), body.role(),
                        request.getHeader("X-User-Id"), request.getHeader("X-User-Role"))));
    }

    // Luồng A: Tự đổi mật khẩu của chính mình
    @PutMapping("/api/v1/auth/password")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangeOwnPasswordRequest body, HttpServletRequest request) {
        String actorId = request.getHeader("X-User-Id");
        if (actorId == null || actorId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu thông tin người dùng đã xác thực.");
        }
        auth.changeOwnPassword(actorId, body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    // Luồng B: SUPER_ADMIN đặt lại mật khẩu cho cấp dưới (CONTENT_ADMIN)
    @PutMapping("/api/v1/auth/users/{userId}/reset-password")
    public ResponseEntity<Void> resetSubordinatePassword(@PathVariable String userId,
                                                         @Valid @RequestBody ResetPasswordRequest body,
                                                         HttpServletRequest request) {
        auth.resetSubordinatePassword(userId, request.getHeader("X-User-Id"), request.getHeader("X-User-Role"), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    // Backward compatibility cho endpoint password cũ
    @PutMapping("/api/v1/auth/users/{userId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable String userId, @Valid @RequestBody ChangePasswordRequest body,
                                                HttpServletRequest request) {
        String actorId = request.getHeader("X-User-Id");
        String actorRole = request.getHeader("X-User-Role");
        if (actorId == null || actorId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Thiếu thông tin người dùng đã xác thực.");
        }

        if ("CONTENT_ADMIN".equals(actorRole)) {
            if (!userId.equals(actorId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Quản trị viên nội dung chỉ được phép đổi mật khẩu của chính mình.");
            }
            if (body.currentPassword() == null || body.currentPassword().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_REQUIRED", "Vui lòng cung cấp mật khẩu hiện tại.");
            }
            auth.changeOwnPassword(actorId, body.currentPassword(), body.newPassword());
        } else if ("SUPER_ADMIN".equals(actorRole)) {
            if (userId.equals(actorId)) {
                if (body.currentPassword() == null || body.currentPassword().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_REQUIRED", "Vui lòng cung cấp mật khẩu hiện tại khi tự đổi mật khẩu.");
                }
                auth.changeOwnPassword(actorId, body.currentPassword(), body.newPassword());
            } else {
                auth.resetSubordinatePassword(userId, actorId, actorRole, body.newPassword());
            }
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này.");
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/auth/users/{userId}/unlock")
    public ApiEnvelope<Map<String,Object>> unlock(@PathVariable String userId, HttpServletRequest request) {
        return ApiEnvelope.success(auth.unlock(userId, request.getHeader("X-User-Id"), request.getHeader("X-User-Role")));
    }

    @PutMapping("/api/v1/auth/users/{userId}/disable")
    public ResponseEntity<Void> disableUser(@PathVariable String userId, HttpServletRequest request) {
        auth.disableUser(userId, request.getHeader("X-User-Id"), request.getHeader("X-User-Role"));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/auth/users/{userId}/enable")
    public ResponseEntity<Void> enableUser(@PathVariable String userId, HttpServletRequest request) {
        auth.enableUser(userId, request.getHeader("X-User-Id"), request.getHeader("X-User-Role"));
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record CreateUserRequest(@NotBlank @Size(max=80) String username, @NotBlank @Size(max=200) String name,
                                   @NotBlank @Size(min=8) String password, @NotBlank String role) {}
    public record ChangeOwnPasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min=8) String newPassword) {}
    public record ResetPasswordRequest(@NotBlank @Size(min=8) String newPassword) {}
    public record ChangePasswordRequest(@NotBlank @Size(min=8) String newPassword, String currentPassword) {}
}
