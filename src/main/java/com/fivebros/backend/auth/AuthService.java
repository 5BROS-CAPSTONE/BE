package com.fivebros.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fivebros.backend.account.EmailAddress;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.account.UserAccountRepository;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.dto.ChangePasswordRequest;
import com.fivebros.backend.auth.dto.LoginRequest;
import com.fivebros.backend.auth.dto.LoginResponse;
import com.fivebros.backend.common.error.ApiException;
import com.fivebros.backend.common.validation.PasswordPolicy;
import com.fivebros.backend.config.JwtProperties;
import com.fivebros.backend.security.AccountPrincipal;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final JwtTokenService tokens;
    private final JwtProperties properties;
    private final PasswordEncoder passwords;
    private final Clock clock;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserAccountRepository users,
            AuthSessionRepository sessions,
            JwtTokenService tokens,
            JwtProperties properties,
            PasswordEncoder passwords,
            Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.properties = properties;
        this.passwords = passwords;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_UTF8_BYTES) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                EmailAddress.normalize(request.email()), request.password()));
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        UserAccount user = users.findByIdForUpdate(principal.id())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        // A concurrent admin password reset must not be followed by a session for the old password.
        if (!principal.passwordHash().equals(user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "The account is disabled");
        }
        Instant now = clock.instant();
        AuthSession session = sessions.save(new AuthSession(user, now, now.plus(properties.tokenTtl())));
        return new LoginResponse(
                tokens.issue(session),
                "Bearer",
                properties.tokenTtl().toSeconds(),
                session.getExpiresAt(),
                UserResponse.from(user));
    }

    @Transactional
    public void changePassword(UUID userId, UUID sessionId, ChangePasswordRequest request) {
        // Serialize with login, admin resets and other password changes for this account.
        UserAccount user = users.findByIdForUpdate(userId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
        Instant now = clock.instant();
        boolean activeSession = sessions.findWithUser(sessionId)
                .filter(session -> session.getUser().getId().equals(userId) && session.isActiveAt(now))
                .isPresent();
        // Authentication may have happened before a concurrent password reset or account disable.
        if (!user.isActive() || !activeSession) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_UTF8_BYTES
                || !passwords.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_INCORRECT", "Current password is incorrect");
        }
        user.changePassword(passwords.encode(request.newPassword()), now);
        sessions.revokeAllForUser(userId, now);
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId) {
        sessions.revoke(sessionId, userId, clock.instant());
    }
}
