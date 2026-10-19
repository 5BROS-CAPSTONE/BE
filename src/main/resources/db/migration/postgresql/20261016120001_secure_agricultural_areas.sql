-- Area data is accessed through the backend API, not Supabase's client Data API.
REVOKE ALL ON TABLE public.farms, public.fields FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.farms, public.fields FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.farms, public.fields FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        REVOKE ALL ON TABLE public.farms, public.fields FROM service_role;
    END IF;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.farms, public.fields TO fivebros_backend;

ALTER TABLE public.farms ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fields ENABLE ROW LEVEL SECURITY;
CREATE POLICY backend_access ON public.farms
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
CREATE POLICY backend_access ON public.fields
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
ALTER TABLE public.farms FORCE ROW LEVEL SECURITY;
ALTER TABLE public.fields FORCE ROW LEVEL SECURITY;
