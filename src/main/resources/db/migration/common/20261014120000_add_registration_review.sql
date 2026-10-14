-- Existing accounts remain active; only public registration creates pending accounts.
ALTER TABLE public.user_accounts ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE public.user_accounts ADD COLUMN requested_role VARCHAR(20);
ALTER TABLE public.user_accounts ADD COLUMN reviewed_by UUID
    REFERENCES public.user_accounts(id) ON DELETE SET NULL;
ALTER TABLE public.user_accounts ADD COLUMN reviewed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE public.user_accounts ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE public.user_accounts ADD CONSTRAINT ck_user_accounts_status
    CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED'));
ALTER TABLE public.user_accounts ADD CONSTRAINT ck_user_accounts_requested_role CHECK (
        requested_role IS NULL OR requested_role IN ('MANAGER', 'EXPERT', 'OPERATOR', 'TECHNICIAN', 'ANALYST')
    );
ALTER TABLE public.user_accounts ADD CONSTRAINT ck_user_accounts_registration_request CHECK (
        status = 'ACTIVE' OR requested_role IS NOT NULL
    );

CREATE INDEX idx_user_accounts_status ON public.user_accounts (status, created_at, id);
