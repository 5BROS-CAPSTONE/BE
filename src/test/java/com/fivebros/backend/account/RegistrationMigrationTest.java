package com.fivebros.backend.account;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationMigrationTest {

    @Test
    void upgradingPreservesExistingAccountsRolesAndSessionsAndEnforcesRegistrationConstraints() {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        // Keep H2's migration session alive for its CHECK-constraint expression comparators.
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(url, "sa", "", true);
        try {
            verifyUpgrade(dataSource);
        } finally {
            dataSource.destroy();
        }
    }

    private void verifyUpgrade(SingleConnectionDataSource dataSource) {
        flyway(dataSource, "20261013161046").migrate();
        JdbcTemplate db = new JdbcTemplate(dataSource);
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        db.update("""
                INSERT INTO app.user_accounts (id, email, full_name, password_hash, created_at, updated_at)
                VALUES (?, 'existing@example.test', 'Existing Admin', 'existing-password-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        db.update("INSERT INTO app.user_roles (user_id, role) VALUES (?, 'ADMIN')", userId);
        db.update("""
                INSERT INTO app.auth_sessions (id, user_id, created_at, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP))
                """, sessionId, userId);

        Flyway upgraded = flyway(dataSource, "latest");
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(2);
        upgraded.validate();
        assertThat(db.queryForObject("SELECT status FROM public.user_accounts WHERE id = ?", String.class, userId))
                .isEqualTo("ACTIVE");
        assertThat(db.queryForObject("SELECT password_hash FROM public.user_accounts WHERE id = ?", String.class, userId))
                .isEqualTo("existing-password-hash");
        assertThat(db.queryForObject("SELECT role FROM public.user_roles WHERE user_id = ?", String.class, userId))
                .isEqualTo("ADMIN");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM public.auth_sessions WHERE id = ?", Integer.class, sessionId))
                .isEqualTo(1);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThatThrownBy(() -> db.update("UPDATE public.user_accounts SET requested_role = 'ADMIN' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE public.user_accounts SET status = 'PENDING' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE public.user_accounts SET status = 'UNKNOWN' WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("INSERT INTO public.user_roles (user_id, role) VALUES (?, 'UNKNOWN')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("UPDATE public.auth_sessions SET expires_at = created_at WHERE id = ?", sessionId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Flyway flyway(SingleConnectionDataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).defaultSchema("app").schemas("app")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .sqlMigrationPrefix("").sqlMigrationSeparator("_").target(target).load();
    }
}
