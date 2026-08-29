-- Vide les tables de donnees METIER du schema public (camions, missions,
-- chauffeurs, etc.), sans toucher aux tables TECHNIQUES posees par le
-- seed Flyway V2 (comptes, roles, permissions, parametres systeme) :
-- le compte admin@sogeco.cm reste utilisable apres le nettoyage.
--
-- flyway_schema_history est egalement preserve (historique des migrations).
-- RESTART IDENTITY : remet les sequences (id auto-increment) a zero.
-- CASCADE : suit les contraintes de cle etrangere entre tables.

DO $$
DECLARE
    tbl RECORD;
    preserved TEXT[] := ARRAY[
        'flyway_schema_history',
        'users', 'roles', 'permissions', 'role_permissions', 'user_roles',
        'system_settings'
    ];
BEGIN
    FOR tbl IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public' AND tablename <> ALL (preserved)
    LOOP
        EXECUTE format('TRUNCATE TABLE public.%I RESTART IDENTITY CASCADE', tbl.tablename);
    END LOOP;
END $$;
