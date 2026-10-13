package com.fivebros.backend.account;

import java.util.Arrays;
import java.util.List;

import com.fivebros.backend.security.Permission;
import com.fivebros.backend.security.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    @GetMapping
    @PreAuthorize("hasAuthority('user:assign-roles')")
    public List<RoleResponse> list() {
        return Arrays.stream(Role.values()).map(RoleResponse::from).toList();
    }

    public record RoleResponse(Role name, String label, List<String> permissions) {
        static RoleResponse from(Role role) {
            return new RoleResponse(role, role.label(),
                    role.permissions().stream().map(Permission::authority).sorted().toList());
        }
    }
}
