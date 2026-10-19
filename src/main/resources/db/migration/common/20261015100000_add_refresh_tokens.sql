ALTER TABLE public.auth_sessions
    ADD COLUMN refresh_token_hash VARCHAR(64);

ALTER TABLE public.auth_sessions
    ADD COLUMN refresh_expires_at TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX uq_auth_sessions_refresh_token_hash
    ON public.auth_sessions (refresh_token_hash);

CREATE INDEX idx_auth_sessions_refresh_expiry
    ON public.auth_sessions (refresh_expires_at);

ALTER TABLE public.auth_sessions
    ADD CONSTRAINT ck_auth_sessions_refresh_token CHECK (
        (refresh_token_hash IS NULL AND refresh_expires_at IS NULL)
        OR (refresh_token_hash IS NOT NULL AND refresh_expires_at > created_at)
    );
