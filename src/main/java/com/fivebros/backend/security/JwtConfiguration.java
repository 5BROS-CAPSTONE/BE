package com.fivebros.backend.security;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.fivebros.backend.config.JwtProperties;

@Configuration
public class JwtConfiguration {

    @Bean
    SecretKey jwtSigningKey(JwtProperties properties, Environment environment) {
        byte[] key;
        if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
            if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("AUTH_JWT_SECRET is required outside local/test profiles");
            }
            key = new byte[32];
            new SecureRandom().nextBytes(key);
        } else {
            try {
                key = Base64.getDecoder().decode(properties.jwtSecret());
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("AUTH_JWT_SECRET must be Base64 encoded");
            }
            if (key.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET must contain at least 32 random bytes");
            }
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
