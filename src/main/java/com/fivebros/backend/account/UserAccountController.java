package com.fivebros.backend.account;

import java.net.URI;
import java.util.UUID;

import com.fivebros.backend.account.dto.AssignRolesRequest;
import com.fivebros.backend.account.dto.CreateUserRequest;
import com.fivebros.backend.account.dto.UpdateUserRequest;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.common.PageResponse;
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
public class UserAccountController {

    private final UserAccountService users;

    public UserAccountController(UserAccountService users) {
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public PageResponse<UserResponse> list(
            @RequestParam(defaultValue = "") @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.list(search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public UserResponse get(@PathVariable UUID id) {
        return users.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = users.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.id())).body(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return users.update(id, request);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public UserResponse assignRoles(@PathVariable UUID id, @Valid @RequestBody AssignRolesRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        return users.assignRoles(UUID.fromString(jwt.getSubject()), id, request.roles());
    }
}
