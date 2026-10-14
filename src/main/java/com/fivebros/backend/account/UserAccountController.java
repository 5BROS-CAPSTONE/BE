package com.fivebros.backend.account;

import java.net.URI;
import java.util.UUID;

import com.fivebros.backend.account.dto.AssignRolesRequest;
import com.fivebros.backend.account.dto.ApproveRegistrationRequest;
import com.fivebros.backend.account.dto.RejectRegistrationRequest;
import com.fivebros.backend.account.dto.CreateUserRequest;
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
@Tag(name = "Users", description = "Admin quản lý tài khoản và duyệt đăng ký")
public class UserAccountController {

    private final UserAccountService users;
    private final RegistrationService registrations;

    public UserAccountController(UserAccountService users, RegistrationService registrations) {
        this.users = users;
        this.registrations = registrations;
    }

    @GetMapping
    @Operation(summary = "Tìm kiếm, lọc trạng thái và phân trang tài khoản",
            description = "Admin. Đặt status=PENDING để xem hàng chờ duyệt; bỏ status để xem mọi trạng thái.")
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
            description = "Tài khoản được tạo trực tiếp ở trạng thái ACTIVE với các role được Admin cấp.")
    @ApiResponse(responseCode = "201", description = "Đã tạo tài khoản ACTIVE")
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa thông tin hoặc đặt lại mật khẩu",
            description = "Admin. Bỏ password hoặc gửi null để giữ mật khẩu hiện tại. "
                    + "Nếu đặt lại mật khẩu, mọi phiên đăng nhập của tài khoản bị thu hồi.")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Thay thế role chính thức của tài khoản ACTIVE",
            description = "Admin. Không dùng để bỏ qua duyệt tài khoản PENDING. Không thể bỏ Admin cuối cùng.")
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return users.assignRoles(UUID.fromString(jwt.getSubject()), id, request.roles());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Duyệt đăng ký và cấp role chính thức",
            description = "Admin. Chỉ nhận PENDING, ít nhất một role nghiệp vụ và không có ADMIN. "
                    + "Có thể cấp role khác requestedRole. Quyền có hiệu lực ngay trên phiên đang dùng.")
    @PreAuthorize("hasAuthority('user:review')")
    public UserResponse approve(@PathVariable UUID id, @Valid @RequestBody ApproveRegistrationRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return registrations.approve(UUID.fromString(jwt.getSubject()), id, request.roles());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối đăng ký với lý do",
            description = "Admin. Chỉ nhận PENDING. Tài khoản chuyển thành REJECTED và mọi phiên bị thu hồi.")
    @PreAuthorize("hasAuthority('user:review')")
    public UserResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectRegistrationRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return registrations.reject(UUID.fromString(jwt.getSubject()), id, request.reason());
    }
}
