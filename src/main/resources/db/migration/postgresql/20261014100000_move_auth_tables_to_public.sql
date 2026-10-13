-- Move application tables into Supabase's familiar public schema without copying data.
-- Keep Flyway's own history table in the private app schema.
DO $$
DECLARE
    app_table_count INTEGER;
    public_table_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO app_table_count
    FROM information_schema.tables
    WHERE table_schema = 'app'
      AND table_name IN ('user_accounts', 'user_roles', 'auth_sessions')
      AND table_type = 'BASE TABLE';

    SELECT COUNT(*) INTO public_table_count
    FROM information_schema.tables
    WHERE table_schema = 'public'
      AND table_name IN ('user_accounts', 'user_roles', 'auth_sessions')
      AND table_type = 'BASE TABLE';

    IF app_table_count = 3 AND public_table_count = 0 THEN
        ALTER TABLE app.user_accounts SET SCHEMA public;
        ALTER TABLE app.user_roles SET SCHEMA public;
        ALTER TABLE app.auth_sessions SET SCHEMA public;
    ELSIF app_table_count = 0 AND public_table_count = 3 THEN
        -- The Supabase migration may have moved the tables before Flyway starts.
        NULL;
    ELSE
        RAISE EXCEPTION
            'Expected all three auth tables in either app or public; found % in app and % in public',
            app_table_count, public_table_count;
    END IF;
END $$;

GRANT USAGE ON SCHEMA public TO fivebros_backend;
REVOKE ALL ON TABLE public.user_accounts, public.user_roles, public.auth_sessions FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.user_accounts, public.user_roles, public.auth_sessions FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.user_accounts, public.user_roles, public.auth_sessions FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        REVOKE ALL ON TABLE public.user_accounts, public.user_roles, public.auth_sessions FROM service_role;
    END IF;
END $$;
