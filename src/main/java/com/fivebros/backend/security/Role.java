package com.fivebros.backend.security;

import java.util.Set;

public enum Role {
    ADMIN("System Administrator", Set.of(Permission.values())),
    MANAGER("Mission Manager", Set.of()),
    EXPERT("Domain & Safety Expert", Set.of()),
    OPERATOR("Drone Operator / Pilot", Set.of()),
    TECHNICIAN("Maintenance Technician", Set.of()),
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
