package com.fivebros.backend.auth;

import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fivebros.backend.account.UserAccountService;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.dto.ChangePasswordRequest;
import com.fivebros.backend.auth.dto.LoginRequest;
import com.fivebros.backend.auth.dto.LoginResponse;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Đăng nhập tài khoản nội bộ, hồ sơ bản thân, đổi mật khẩu và đăng xuất")
public class AuthController {

    private final AuthService authentication;
    private final UserAccountService users;

    public AuthController(AuthService authentication, UserAccountService users) {
        this.authentication = authentication;
        this.users = users;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(
            summary = "Đăng nhập và lấy JWT",
            description = "Dùng email và mật khẩu do Admin cung cấp. Copy accessToken vào nút Authorize.")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authentication.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất và thu hồi phiên đang dùng")
    @ApiResponse(responseCode = "204", description = "Đã thu hồi phiên hiện tại")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        authentication.logout(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    @Operation(
            summary = "Tự đổi mật khẩu",
            description =
                    "Mọi tài khoản đang hoạt động được đổi mật khẩu của chính mình. Tài khoản được lấy từ JWT; "
                            + "phải nhập đúng currentPassword. Thành công sẽ thu hồi mọi phiên, kể cả phiên hiện tại; đăng nhập lại bằng newPassword.")
    @ApiResponse(responseCode = "204", description = "Đã đổi mật khẩu và thu hồi mọi phiên")
    @ApiResponse(responseCode = "400", description = "Mật khẩu hiện tại không đúng hoặc mật khẩu mới không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập hoặc phiên không còn hiệu lực")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(
            summary = "Xem hồ sơ và trạng thái tài khoản bản thân",
            description = "Quyền được lấy từ role hiện tại trong database.")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.get(UUID.fromString(jwt.getSubject()));
    }
}
