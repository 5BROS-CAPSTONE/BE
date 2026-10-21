package com.fivebros.backend.security;

import java.util.Set;

public enum Role {
    ADMIN("System Administrator", Set.of(Permission.values())),
    MANAGER("Mission Manager", Set.of(
            Permission.AREA_READ, Permission.AREA_CREATE, Permission.AREA_UPDATE,
            Permission.UAV_PROFILE_READ, Permission.DRONE_READ)),
    EXPERT("Domain & Safety Expert", Set.of(
            Permission.UAV_PROFILE_READ, Permission.UAV_PROFILE_CREATE, Permission.UAV_PROFILE_UPDATE)),
    OPERATOR("Drone Operator / Pilot", Set.of()),
    TECHNICIAN("Maintenance Technician", Set.of(
            Permission.UAV_PROFILE_READ, Permission.DRONE_READ, Permission.DRONE_CREATE, Permission.DRONE_UPDATE)),
    ANALYST("Data Analyst", Set.of());

    private final String label;
    private final Set<Permission> permissions;

    Role(String label, Set<Permission> permissions) {
        this.label = label;
        this.permissions = permissions;
    }

    public String label() {
        return label;
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
