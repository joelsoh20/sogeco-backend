-- =====================================================================
-- V16 — Categorie de maintenance "Laverie" et station-service
--        de l'entreprise
-- =====================================================================


-- ---------------------------------------------------------------------
-- Nouvelle categorie de maintenance : frais de laverie (lavage des
-- camions). Postgres n'autorise pas d'ajouter une valeur a un CHECK
-- existant : on le redefinit avec la liste complete.
-- ---------------------------------------------------------------------
ALTER TABLE maintenance_logs DROP CONSTRAINT ck_maintenance_category;
ALTER TABLE maintenance_logs ADD CONSTRAINT ck_maintenance_category CHECK (category IN
    ('ENTRETIEN_PREVENTIF', 'REPARATION_MECANIQUE', 'PNEUMATIQUE', 'ELECTRICITE', 'LAVERIE', 'AUTRES'));


-- ---------------------------------------------------------------------
-- Station-service de l'entreprise.
-- ---------------------------------------------------------------------
INSERT INTO partners (name, partner_type, created_by)
VALUES ('AFRICA PETROLEUM S.A', 'STATION_SERVICE', 'system');
