-- FLT-01/02/03. Technical limits are owned by the profile, not individual drones.
CREATE TABLE public.uav_profiles (
    id UUID PRIMARY KEY,
    manufacturer VARCHAR(100) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    identity_key VARCHAR(261) NOT NULL UNIQUE,
    empty_weight_kg NUMERIC(18, 6),
    max_takeoff_weight_kg NUMERIC(18, 6),
    max_payload_kg NUMERIC(18, 6),
    battery_capacity_ah NUMERIC(18, 6),
    battery_energy_wh NUMERIC(18, 6),
    max_wind_speed_mps NUMERIC(18, 6),
    min_operating_temperature_c NUMERIC(18, 6),
    max_operating_temperature_c NUMERIC(18, 6),
    max_speed_mps NUMERIC(18, 6),
    max_flight_time_minutes NUMERIC(18, 6),
    reserve_energy_wh NUMERIC(18, 6),
    energy_consumption_wh_per_km NUMERIC(18, 6),
    battery_count INTEGER NOT NULL,
    source_url VARCHAR(2000),
    source_accessed_on DATE,
    source_recorded_by VARCHAR(160),
    data_status VARCHAR(24) NOT NULL,
    notes VARCHAR(8000),
    created_by UUID NOT NULL REFERENCES public.user_accounts(id),
    updated_by UUID NOT NULL REFERENCES public.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_uav_profile_identity CHECK (LENGTH(TRIM(manufacturer)) > 0 AND LENGTH(TRIM(model_name)) > 0),
    CONSTRAINT ck_uav_profile_battery_count CHECK (battery_count > 0),
    CONSTRAINT ck_uav_profile_data_status CHECK (data_status IN ('UNVERIFIED', 'VERIFIED', 'DATASHEET_INCOMPLETE')),
    CONSTRAINT ck_uav_empty_weight_kg CHECK (empty_weight_kg IS NULL OR empty_weight_kg > 0),
    CONSTRAINT ck_uav_max_takeoff_weight_kg CHECK (max_takeoff_weight_kg IS NULL OR max_takeoff_weight_kg > 0),
    CONSTRAINT ck_uav_max_payload_kg CHECK (max_payload_kg IS NULL OR max_payload_kg >= 0),
    CONSTRAINT ck_uav_battery_capacity_ah CHECK (battery_capacity_ah IS NULL OR battery_capacity_ah > 0),
    CONSTRAINT ck_uav_battery_energy_wh CHECK (battery_energy_wh IS NULL OR battery_energy_wh > 0),
    CONSTRAINT ck_uav_max_wind_speed_mps CHECK (max_wind_speed_mps IS NULL OR max_wind_speed_mps >= 0),
    CONSTRAINT ck_uav_max_speed_mps CHECK (max_speed_mps IS NULL OR max_speed_mps > 0),
    CONSTRAINT ck_uav_max_flight_time_minutes CHECK (max_flight_time_minutes IS NULL OR max_flight_time_minutes > 0),
    CONSTRAINT ck_uav_reserve_energy_wh CHECK (reserve_energy_wh IS NULL OR reserve_energy_wh >= 0),
    CONSTRAINT ck_uav_energy_consumption_wh_per_km CHECK (energy_consumption_wh_per_km IS NULL OR energy_consumption_wh_per_km > 0),
    CONSTRAINT ck_uav_temperature_absolute CHECK (
        (min_operating_temperature_c IS NULL OR min_operating_temperature_c >= -273.15)
        AND (max_operating_temperature_c IS NULL OR max_operating_temperature_c >= -273.15)),
    CONSTRAINT ck_uav_temperature_range CHECK (
        min_operating_temperature_c IS NULL OR max_operating_temperature_c IS NULL
        OR min_operating_temperature_c <= max_operating_temperature_c),
    CONSTRAINT ck_uav_empty_weight CHECK (
        empty_weight_kg IS NULL OR max_takeoff_weight_kg IS NULL OR empty_weight_kg <= max_takeoff_weight_kg),
    CONSTRAINT ck_uav_payload CHECK (
        max_payload_kg IS NULL OR max_takeoff_weight_kg IS NULL OR max_payload_kg <= max_takeoff_weight_kg)
);

CREATE TABLE public.uav_profile_capabilities (
    uav_profile_id UUID NOT NULL REFERENCES public.uav_profiles(id),
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (uav_profile_id, capability),
    CONSTRAINT ck_uav_capability CHECK (LENGTH(TRIM(capability)) > 0)
);

CREATE TABLE public.drones (
    id UUID PRIMARY KEY,
    serial VARCHAR(100) NOT NULL,
    serial_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    uav_profile_id UUID NOT NULL REFERENCES public.uav_profiles(id),
    status VARCHAR(16) NOT NULL,
    created_by UUID NOT NULL REFERENCES public.user_accounts(id),
    updated_by UUID NOT NULL REFERENCES public.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_drone_identity CHECK (LENGTH(TRIM(serial)) > 0 AND LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_drone_status CHECK (status IN ('AVAILABLE', 'IN_MISSION', 'UNAVAILABLE', 'RETIRED'))
);

CREATE INDEX idx_uav_profiles_created ON public.uav_profiles (created_at, id);
CREATE INDEX idx_drones_profile_status ON public.drones (uav_profile_id, status);
CREATE INDEX idx_drones_created ON public.drones (created_at, id);

