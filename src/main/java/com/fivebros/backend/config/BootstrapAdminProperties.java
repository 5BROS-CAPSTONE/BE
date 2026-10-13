package com.fivebros.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String password, String fullName) {
    @Override
    public String toString() {
        return "BootstrapAdminProperties[enabled=" + enabled + ", credentials=REDACTED]";
    }
}
