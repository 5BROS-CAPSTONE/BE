package com.fivebros.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private final SecureRandom random = new SecureRandom();

    public IssuedRefreshToken issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedRefreshToken(value, hash(value));
    }

    public String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedRefreshToken(String value, String hash) {
        @Override
        public String toString() {
            return "IssuedRefreshToken[value=REDACTED, hash=REDACTED]";
        }
    }
}
