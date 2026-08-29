-- =====================================================================
-- V17 — Usage du camion (voyage / tour de ville) et lavage hebdomadaire
--
-- Un camion en tour de ville a droit a un lavage chaque samedi, deduit
-- automatiquement comme charge de maintenance (categorie LAVERIE,
-- ajoutee en V16). Le montant (2500 ou 3000 FCFA) est fixe camion par
-- camion, saisi au formulaire d'ajout/modification.
-- =====================================================================

ALTER TABLE vehicles ADD COLUMN usage_type       VARCHAR(20) NOT NULL DEFAULT 'VOYAGE';
ALTER TABLE vehicles ADD COLUMN weekly_wash_cost  NUMERIC(15,2);

ALTER TABLE vehicles ADD CONSTRAINT ck_vehicles_usage_type CHECK (usage_type IN
    ('VOYAGE', 'TOUR_VILLE'));

ALTER TABLE vehicles ADD CONSTRAINT ck_vehicles_wash_cost CHECK (
    weekly_wash_cost IS NULL OR weekly_wash_cost IN (2500, 3000));
