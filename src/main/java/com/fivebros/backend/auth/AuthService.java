package com.fivebros.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fivebros.backend.account.EmailAddress;
import com.fivebros.backend.account.UserAccount;
import com.fivebros.backend.account.UserAccountRepository;
import com.fivebros.backend.account.dto.UserResponse;
import com.fivebros.backend.auth.dto.LoginRequest;
import com.fivebros.backend.auth.dto.LoginResponse;
import com.fivebros.backend.config.JwtProperties;
import com.fivebros.backend.security.AccountPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final AuthSessionRepository sessions;
    private final JwtTokenService tokens;
    private final JwtProperties properties;
    private final Clock clock;

    public AuthService(AuthenticationManager authenticationManager, UserAccountRepository users,
                       AuthSessionRepository sessions, JwtTokenService tokens,
                       JwtProperties properties, Clock clock) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.sessions = sessions;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadCredentialsException("Invalid credentials");
        }
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailAddress.normalize(request.email()), request.password()));
        AccountPrincipal principal = (AccountPrincipal) authentication.getPrincipal();
        UserAccount user = users.findByIdForUpdate(principal.id())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        // A concurrent admin password reset must not be followed by a session for the old password.
        if (!principal.passwordHash().equals(user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        Instant now = clock.instant();
        AuthSession session = sessions.save(new AuthSession(user, now, now.plus(properties.tokenTtl())));
        return new LoginResponse(tokens.issue(session), "Bearer", properties.tokenTtl().toSeconds(),
                session.getExpiresAt(), UserResponse.from(user));
    }

    @Transactional
    public void logout(UUID userId, UUID sessionId) {
        sessions.revoke(sessionId, userId, clock.instant());
    }
}
