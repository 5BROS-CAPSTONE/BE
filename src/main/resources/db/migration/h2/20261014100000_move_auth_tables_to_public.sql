-- H2 cannot ALTER TABLE ... SET SCHEMA. Recreate constraints and copy all existing data.
CREATE TABLE public.user_accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_accounts_email UNIQUE (email),
    CONSTRAINT ck_user_accounts_email_normalized CHECK (email = LOWER(TRIM(email)))
);

INSERT INTO public.user_accounts SELECT * FROM app.user_accounts;

CREATE TABLE public.user_roles (
    user_id UUID NOT NULL REFERENCES public.user_accounts(id),
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_known_role CHECK (
        role IN ('ADMIN', 'MANAGER', 'EXPERT', 'OPERATOR', 'TECHNICIAN', 'ANALYST')
    )
);
CREATE INDEX idx_user_roles_role ON public.user_roles (role, user_id);

INSERT INTO public.user_roles SELECT * FROM app.user_roles;

CREATE TABLE public.auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES public.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > created_at)
);
CREATE INDEX idx_auth_sessions_user ON public.auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_expiry ON public.auth_sessions (expires_at);

INSERT INTO public.auth_sessions SELECT * FROM app.auth_sessions;

DROP TABLE app.auth_sessions;
DROP TABLE app.user_roles;
DROP TABLE app.user_accounts;
