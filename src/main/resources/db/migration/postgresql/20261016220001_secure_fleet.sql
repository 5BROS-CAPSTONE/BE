-- Same backend-only access policy as existing area and account resources.
REVOKE ALL ON TABLE public.uav_profiles, public.uav_profile_capabilities, public.drones FROM PUBLIC;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.uav_profiles, public.uav_profile_capabilities, public.drones FROM anon;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        REVOKE ALL ON TABLE public.uav_profiles, public.uav_profile_capabilities, public.drones FROM authenticated;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        REVOKE ALL ON TABLE public.uav_profiles, public.uav_profile_capabilities, public.drones FROM service_role;
    END IF;
END $$;

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.uav_profiles, public.uav_profile_capabilities, public.drones TO fivebros_backend;

ALTER TABLE public.uav_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY backend_access ON public.uav_profiles
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
ALTER TABLE public.uav_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE public.uav_profile_capabilities ENABLE ROW LEVEL SECURITY;
CREATE POLICY backend_access ON public.uav_profile_capabilities
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
ALTER TABLE public.uav_profile_capabilities FORCE ROW LEVEL SECURITY;
ALTER TABLE public.drones ENABLE ROW LEVEL SECURITY;
CREATE POLICY backend_access ON public.drones
    FOR ALL TO fivebros_backend USING (true) WITH CHECK (true);
ALTER TABLE public.drones FORCE ROW LEVEL SECURITY;

