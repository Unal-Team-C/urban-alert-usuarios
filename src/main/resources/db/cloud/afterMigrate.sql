-- Flyway callback, loaded only by the "migrate" profile (cloud migration task; spec 001-migracion-aws RF-010,
-- plan D-003/D-007). Runs after every migrate as the RDS master user and is idempotent: creates the application
-- role if missing, re-synchronizes its password from Secrets Manager and grants data access (no DDL) to the
-- current tables and to the ones future migrations create. ${app_user}/${app_password} are Flyway placeholders.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${app_user}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN', '${app_user}');
    END IF;
    EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', '${app_user}', '${app_password}');

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), '${app_user}');
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', '${app_user}');
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', '${app_user}');
    EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
                   '${app_user}');

    -- The migration history belongs to the migration task only.
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE format('REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM %I', '${app_user}');
    END IF;
END
$$;
