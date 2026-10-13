-- Spring Boot accesses this private schema through JDBC. It is not a frontend Data API.
REVOKE ALL ON SCHEMA app FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA app FROM PUBLIC;

ALTER TABLE app.user_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.auth_sessions ENABLE ROW LEVEL SECURITY;

-- Plain PostgreSQL installations may not have Supabase's built-in API roles.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON SCHEMA app FROM anon;
        REVOKE ALL ON ALL TABLES IN SCHEMA app FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON SCHEMA app FROM authenticated;
        REVOKE ALL ON ALL TABLES IN SCHEMA app FROM authenticated;
    END IF;
END $$;
