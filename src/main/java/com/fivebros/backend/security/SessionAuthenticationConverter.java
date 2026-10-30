package com.fivebros.backend.security;

import java.time.Clock;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fivebros.backend.auth.AuthSession;
import com.fivebros.backend.auth.AuthSessionRepository;

@Component
public class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AuthSessionRepository sessions;
    private final Clock clock;

    public SessionAuthenticationConverter(AuthSessionRepository sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())) {
            throw invalidSession();
        }
        UUID sessionId;
        UUID userId;
        try {
            sessionId = UUID.fromString(jwt.getId());
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalidSession();
        }
        AuthSession session = sessions.findWithUser(sessionId).orElseThrow(this::invalidSession);
        if (!session.isActiveAt(clock.instant())
                || !session.getUser().getId().equals(userId)
                || !session.getUser().isActive()) {
            throw invalidSession();
        }
        return new JwtAuthenticationToken(
                jwt, RoleAuthorities.from(session.getUser().getRoles()), userId.toString());
    }

    private InvalidBearerTokenException invalidSession() {
        return new InvalidBearerTokenException("The authentication session is invalid or has expired");
    }
}
