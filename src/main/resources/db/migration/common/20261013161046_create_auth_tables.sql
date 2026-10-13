CREATE TABLE app.user_accounts (
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

CREATE TABLE app.user_roles (
    user_id UUID NOT NULL REFERENCES app.user_accounts(id),
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_roles_known_role CHECK (
        role IN ('ADMIN', 'MANAGER', 'EXPERT', 'OPERATOR', 'TECHNICIAN', 'ANALYST')
    )
);

CREATE INDEX idx_user_roles_role ON app.user_roles (role, user_id);

CREATE TABLE app.auth_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app.user_accounts(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_auth_sessions_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_auth_sessions_user ON app.auth_sessions (user_id);
CREATE INDEX idx_auth_sessions_expiry ON app.auth_sessions (expires_at);
