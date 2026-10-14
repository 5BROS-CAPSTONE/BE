package com.fivebros.backend.account;

import java.net.URI;
import java.util.UUID;

import com.fivebros.backend.account.dto.AssignRolesRequest;
import com.fivebros.backend.account.dto.CreateUserRequest;
import com.fivebros.backend.account.dto.ResetPasswordRequest;
import com.fivebros.backend.account.dto.UpdateUserRequest;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Admin tạo tài khoản nội bộ, cung cấp mật khẩu và cấp role")
public class UserAccountController {

    private final UserAccountService users;

    public UserAccountController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm, lọc trạng thái và phân trang tài khoản",
            description = "Admin. Tìm theo email hoặc họ tên; lọc trạng thái tài khoản nếu cần.")
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "") @Size(max = 120) String search,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(search, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết tài khoản", description = "Admin.")
    @PreAuthorize("hasAuthority('user:read')")
    public UserResponse get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    @Operation(summary = "Admin tạo tài khoản trực tiếp",
            description = "Admin nhập email, họ tên, mật khẩu và ít nhất một role. Tài khoản dùng được ngay; "
                    + "Admin cung cấp thông tin đăng nhập cho người dùng.")
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản ACTIVE")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin và trạng thái tài khoản",
            description = "Admin. Cập nhật email và họ tên; status là tùy chọn. "
                    + "DISABLED khóa đăng nhập và thu hồi mọi phiên. ACTIVE cần có role. Không thể khóa Admin cuối cùng.")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return users.update(UUID.fromString(jwt.getSubject()), id, request);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Đặt lại mật khẩu tài khoản",
            description = "Admin. Tài khoản được xác định bằng id; email và họ tên trong phản hồi được lấy từ DB. "
                    + "Mọi phiên đăng nhập hiện tại của tài khoản sẽ bị thu hồi.")
    @ApiResponse(responseCode = "200", description = "Đã đặt lại mật khẩu")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        return users.resetPassword(id, request.password());
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Thay thế role của tài khoản",
            description = "Admin. Thay thế toàn bộ tập role bằng ít nhất một role. Không thể bỏ Admin cuối cùng.")
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return users.assignRoles(UUID.fromString(jwt.getSubject()), id, request.roles());
    }

}
