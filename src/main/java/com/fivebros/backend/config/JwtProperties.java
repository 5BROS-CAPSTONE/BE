package com.fivebros.backend.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.auth")
public record JwtProperties(
        @NotBlank String issuer,
        String jwtSecret,
        @NotNull Duration tokenTtl
) {
    public JwtProperties {
        if (tokenTtl != null && (tokenTtl.isZero() || tokenTtl.isNegative())) {
            throw new IllegalArgumentException("Token TTL must be positive");
        }
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", tokenTtl=" + tokenTtl + ", secret=REDACTED]";
    }
}
