package com.fivebros.backend.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

    @EntityGraph(attributePaths = {"user", "user.roles"})
    @Query("select session from AuthSession session where session.id = :id")
    Optional<AuthSession> findWithUser(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AuthSession session set session.revokedAt = :now
            where session.user.id = :userId and session.revokedAt is null
            """)
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update AuthSession session set session.revokedAt = :now
            where session.id = :sessionId and session.user.id = :userId
              and session.revokedAt is null
            """)
    int revoke(@Param("sessionId") UUID sessionId, @Param("userId") UUID userId, @Param("now") Instant now);
}
