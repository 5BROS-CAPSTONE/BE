-- Match the Supabase table schema in local H2 development.
ALTER TABLE app.user_accounts SET SCHEMA public;
ALTER TABLE app.user_roles SET SCHEMA public;
ALTER TABLE app.auth_sessions SET SCHEMA public;
