package com.fivebros.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class InternalAccountsMigrationTest {

    @Test
    void upgradePreservesDataAndLocksLegacyRegistrationsWithoutChangingActiveAccounts() {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
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
        UUID adminId = UUID.randomUUID();
        UUID activeSessionId = UUID.randomUUID();
        db.update("""
                INSERT INTO app.user_accounts (id, email, full_name, password_hash, created_at, updated_at)
                VALUES (?, 'existing@example.test', 'Existing Admin', 'existing-password-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, adminId);
        db.update("INSERT INTO app.user_roles (user_id, role) VALUES (?, 'ADMIN')", adminId);
        db.update("""
                INSERT INTO app.auth_sessions (id, user_id, created_at, expires_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP))
                """, activeSessionId, adminId);
        assertThat(flyway(dataSource, "20261014120000").migrate().migrationsExecuted)
                .isEqualTo(2);

        UUID pendingId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        for (UUID id : new UUID[] {pendingId, rejectedId}) {
            db.update("""
                    INSERT INTO public.user_accounts
                        (id, email, full_name, password_hash, created_at, updated_at, status, requested_role)
                    VALUES (?, ?, 'Legacy User', 'legacy-password-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, 'OPERATOR')
                    """, id, id + "@example.test", id.equals(pendingId) ? "PENDING" : "REJECTED");
            db.update("""
                    INSERT INTO public.auth_sessions (id, user_id, created_at, expires_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP, DATEADD('MINUTE', 30, CURRENT_TIMESTAMP))
                    """, UUID.randomUUID(), id);
        }
        db.update("""
                UPDATE public.user_accounts
                SET reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, rejection_reason = 'Historical reason'
                WHERE id = ?
                """, adminId, rejectedId);

        Flyway upgraded = flyway(dataSource, "20261014120001");
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        upgraded.validate();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM public.user_accounts", Integer.class))
                .isEqualTo(3);
        assertThat(db.queryForObject("SELECT status FROM public.user_accounts WHERE id = ?", String.class, adminId))
                .isEqualTo("ACTIVE");
        assertThat(db.queryForObject(
                        "SELECT password_hash FROM public.user_accounts WHERE id = ?", String.class, adminId))
                .isEqualTo("existing-password-hash");
        assertThat(db.queryForObject("SELECT role FROM public.user_roles WHERE user_id = ?", String.class, adminId))
                .isEqualTo("ADMIN");
        assertThat(db.queryForObject(
                        "SELECT COUNT(*) FROM public.auth_sessions WHERE id = ? AND revoked_at IS NULL",
                        Integer.class,
                        activeSessionId))
                .isEqualTo(1);
        for (UUID id : new UUID[] {pendingId, rejectedId}) {
            assertThat(db.queryForObject("SELECT status FROM public.user_accounts WHERE id = ?", String.class, id))
                    .isEqualTo("DISABLED");
            assertThat(db.queryForObject(
                            "SELECT password_hash FROM public.user_accounts WHERE id = ?", String.class, id))
                    .isEqualTo("legacy-password-hash");
            assertThat(db.queryForObject(
                            "SELECT requested_role FROM public.user_accounts WHERE id = ?", String.class, id))
                    .isEqualTo("OPERATOR");
            assertThat(db.queryForObject(
                            "SELECT COUNT(*) FROM public.auth_sessions WHERE user_id = ? AND revoked_at IS NULL",
                            Integer.class,
                            id))
                    .isZero();
        }
        assertThat(db.queryForObject(
                        "SELECT rejection_reason FROM public.user_accounts WHERE id = ?", String.class, rejectedId))
                .isEqualTo("Historical reason");
        assertThat(db.queryForObject(
                        "SELECT reviewed_by FROM public.user_accounts WHERE id = ?", UUID.class, rejectedId))
                .isEqualTo(adminId);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        for (String invalidStatus : new String[] {"PENDING", "REJECTED", "UNKNOWN"}) {
            assertThatThrownBy(() -> db.update(
                            "UPDATE public.user_accounts SET status = ? WHERE id = ?", invalidStatus, adminId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(
                        () -> db.update("INSERT INTO public.user_roles (user_id, role) VALUES (?, 'UNKNOWN')", adminId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update(
                        "UPDATE public.auth_sessions SET expires_at = created_at WHERE id = ?", activeSessionId))
                .isInstanceOf(DataIntegrityViolationException.class);
        db.update("UPDATE public.user_accounts SET status = 'DISABLED' WHERE id = ?", adminId);
        assertThat(db.queryForObject("SELECT status FROM public.user_accounts WHERE id = ?", String.class, adminId))
                .isEqualTo("DISABLED");
    }

    private Flyway flyway(SingleConnectionDataSource dataSource, String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .defaultSchema("app")
                .schemas("app")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .sqlMigrationPrefix("")
                .sqlMigrationSeparator("_")
                .target(target)
                .load();
    }
}
