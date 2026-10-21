ALTER TABLE public.farms
    ADD COLUMN location_latitude DOUBLE PRECISION;

ALTER TABLE public.farms
    ADD COLUMN location_longitude DOUBLE PRECISION;

ALTER TABLE public.farms
    ADD CONSTRAINT ck_farms_location CHECK (
        (location_latitude IS NULL AND location_longitude IS NULL) OR
        (location_latitude IS NOT NULL AND location_longitude IS NOT NULL
         AND location_latitude BETWEEN -90 AND 90
         AND location_longitude BETWEEN -180 AND 180)
    );
