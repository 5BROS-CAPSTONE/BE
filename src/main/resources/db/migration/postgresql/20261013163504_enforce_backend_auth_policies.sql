-- Only the dedicated Spring Boot database role may access these tables.
CREATE POLICY backend_access ON app.user_accounts
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
CREATE POLICY backend_access ON app.user_roles
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
CREATE POLICY backend_access ON app.auth_sessions
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);

ALTER TABLE app.user_accounts FORCE ROW LEVEL SECURITY;
ALTER TABLE app.user_roles FORCE ROW LEVEL SECURITY;
ALTER TABLE app.auth_sessions FORCE ROW LEVEL SECURITY;
