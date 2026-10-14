package com.fivebros.backend.auth;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fivebros.backend.account.RegistrationService;
import com.fivebros.backend.account.UserAccountService;
import com.fivebros.backend.account.dto.RegisterUserRequest;
import com.fivebros.backend.account.dto.RegistrationResponse;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.dto.LoginRequest;
import com.fivebros.backend.auth.dto.LoginResponse;
import com.fivebros.backend.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, hồ sơ bản thân và đăng xuất")
public class AuthController {

    private final AuthService authentication;
    private final UserAccountService users;
    private final RegistrationService registrations;

    public AuthController(AuthService authentication, UserAccountService users, RegistrationService registrations) {
        this.authentication = authentication;
        this.users = users;
        this.registrations = registrations;
    }

    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Đăng ký tài khoản chờ duyệt",
            description = "Lưu requestedRole riêng, chưa cấp role chính thức. Sau khi đăng ký có thể đăng nhập; "
                    + "tài khoản PENDING chỉ được xem hồ sơ và đăng xuất. Không được yêu cầu ADMIN.")
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản PENDING, chưa cấp JWT")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(registrations.register(request));
    }

    @GetMapping("/registration-roles")
    @SecurityRequirements
    @Operation(summary = "Danh sách role có thể yêu cầu khi đăng ký")
    public List<RegistrationRoleResponse> registrationRoles() {
        return Arrays.stream(Role.values()).filter(role -> role != Role.ADMIN)
                .map(role -> new RegistrationRoleResponse(role, role.label())).toList();
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Đăng nhập và lấy JWT",
            description = "PENDING vẫn đăng nhập được với roles rỗng. Copy accessToken vào nút Authorize. "
                    + "Mật khẩu sai trả 401; tài khoản REJECTED với mật khẩu đúng trả 403.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất và thu hồi phiên đang dùng", description = "Dùng được cho cả PENDING và ACTIVE.")
    @ApiResponse(responseCode = "204", description = "Đã thu hồi phiên hiện tại")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authentication.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Xem hồ sơ và trạng thái tài khoản bản thân",
            description = "Dùng được cho cả PENDING và ACTIVE. Quyền được cập nhật sau khi Admin duyệt "
                    + "mà không cần đăng nhập lại.")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.get(UUID.fromString(jwt.getSubject()));
    }

    public record RegistrationRoleResponse(Role name, String label) {
    }
}
