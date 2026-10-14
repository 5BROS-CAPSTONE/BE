-- Keep previously applied migrations and registration metadata for historical data.
-- Internal accounts only use ACTIVE / DISABLED; administrators provision accounts and roles.
ALTER TABLE public.user_accounts DROP CONSTRAINT ck_user_accounts_registration_request;
ALTER TABLE public.user_accounts DROP CONSTRAINT ck_user_accounts_status;

UPDATE public.auth_sessions
SET revoked_at = CURRENT_TIMESTAMP
WHERE revoked_at IS NULL
  AND user_id IN (SELECT id FROM public.user_accounts WHERE status IN ('PENDING', 'REJECTED'));

UPDATE public.user_accounts
SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP, version = version + 1
WHERE status IN ('PENDING', 'REJECTED');

ALTER TABLE public.user_accounts ADD CONSTRAINT ck_user_accounts_status
    CHECK (status IN ('ACTIVE', 'DISABLED'));
