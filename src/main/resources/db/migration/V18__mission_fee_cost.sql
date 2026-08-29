-- =====================================================================
-- V18 — Frais de mission (indemnite de voyage du chauffeur)
--
-- Distinct de driver_cost (quote-part de salaire imputee automatiquement
-- a la cloture) : le frais de mission est une somme forfaitaire connue
-- des la planification d'un voyage hors ville, saisie a la creation.
--
-- total_cost et margin_amount sont des colonnes calculees (GENERATED
-- ALWAYS) : Postgres n'autorise pas de modifier leur expression sans
-- les recreer.
-- =====================================================================

ALTER TABLE missions ADD COLUMN mission_fee_cost NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE missions ADD CONSTRAINT ck_missions_mission_fee CHECK (mission_fee_cost >= 0);

ALTER TABLE missions DROP COLUMN total_cost;
ALTER TABLE missions DROP COLUMN margin_amount;

ALTER TABLE missions ADD COLUMN total_cost NUMERIC(15,2) GENERATED ALWAYS AS
    (fuel_cost + toll_cost + driver_cost + other_cost + mission_fee_cost) STORED;
ALTER TABLE missions ADD COLUMN margin_amount NUMERIC(15,2) GENERATED ALWAYS AS
    (revenue_amount - fuel_cost - toll_cost - driver_cost - other_cost - mission_fee_cost) STORED;
