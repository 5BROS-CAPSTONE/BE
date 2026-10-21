package com.fivebros.backend.security;

public enum Permission {
    USER_READ("user:read"),
    USER_CREATE("user:create"),
    USER_UPDATE("user:update"),
    USER_ASSIGN_ROLES("user:assign-roles"),
    AREA_READ("area:read"),
    AREA_CREATE("area:create"),
    AREA_UPDATE("area:update"),
    UAV_PROFILE_READ("uav-profile:read"),
    UAV_PROFILE_CREATE("uav-profile:create"),
    UAV_PROFILE_UPDATE("uav-profile:update"),
    DRONE_READ("drone:read"),
    DRONE_CREATE("drone:create"),
    DRONE_UPDATE("drone:update");

    private final String authority;

    Permission(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }
}
