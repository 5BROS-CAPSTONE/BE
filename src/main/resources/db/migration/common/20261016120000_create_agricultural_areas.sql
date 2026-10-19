CREATE TABLE public.farms (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES public.user_accounts(id),
    updated_by UUID NOT NULL REFERENCES public.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_farms_name CHECK (LENGTH(TRIM(name)) > 0)
);

CREATE TABLE public.fields (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES public.farms(id),
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000),
    reference_latitude DOUBLE PRECISION,
    reference_longitude DOUBLE PRECISION,
    boundary_geojson TEXT,
    boundary_source VARCHAR(24) NOT NULL,
    declared_area_m2 NUMERIC(24, 6),
    computed_area_m2 NUMERIC(24, 6),
    perimeter_m NUMERIC(24, 6),
    area_source VARCHAR(16) NOT NULL,
    created_by UUID NOT NULL REFERENCES public.user_accounts(id),
    updated_by UUID NOT NULL REFERENCES public.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_fields_name CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_fields_reference CHECK (
        (reference_latitude IS NULL AND reference_longitude IS NULL) OR
        (reference_latitude IS NOT NULL AND reference_longitude IS NOT NULL
         AND reference_latitude BETWEEN -90 AND 90 AND reference_longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT ck_fields_declared_area CHECK (declared_area_m2 IS NULL OR declared_area_m2 > 0),
    CONSTRAINT ck_fields_area_source CHECK (
        (area_source = 'MANUAL' AND declared_area_m2 IS NOT NULL AND boundary_geojson IS NULL
         AND boundary_source = 'NONE' AND computed_area_m2 IS NULL AND perimeter_m IS NULL) OR
        (area_source = 'BOUNDARY' AND boundary_geojson IS NOT NULL
         AND boundary_source IN ('MANUAL_COORDINATES', 'KML')
         AND computed_area_m2 IS NOT NULL AND computed_area_m2 > 0
         AND perimeter_m IS NOT NULL AND perimeter_m > 0)
    )
);

CREATE INDEX idx_farms_created ON public.farms (created_at, id);
CREATE INDEX idx_fields_farm_created ON public.fields (farm_id, created_at, id);
