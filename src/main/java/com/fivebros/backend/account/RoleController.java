package com.fivebros.backend.account;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fivebros.backend.security.Permission;
import com.fivebros.backend.security.Role;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles", description = "Danh sách role và quyền trong hệ thống")
public class RoleController {

    @GetMapping
    @Operation(summary = "Danh sách role và permission", description = "Admin.")
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public List<RoleResponse> list() {
        return Arrays.stream(Role.values()).map(RoleResponse::from).toList();
    }

    public record RoleResponse(Role name, String label, List<String> permissions) {
        static RoleResponse from(Role role) {
            return new RoleResponse(
                    role,
                    role.label(),
                    role.permissions().stream()
                            .map(Permission::authority)
                            .sorted()
                            .toList());
        }
    }
}
