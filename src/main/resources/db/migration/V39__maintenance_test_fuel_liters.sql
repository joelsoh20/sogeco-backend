-- Carburant consomme au garage pour les essais du vehicule pendant une
-- intervention de maintenance, saisi par le gestionnaire ou l'admin.
-- Facultatif : une intervention sans essai routier n'en consomme pas.
ALTER TABLE maintenance_logs
    ADD COLUMN test_fuel_liters NUMERIC(8, 2);

ALTER TABLE maintenance_logs
    ADD CONSTRAINT ck_maintenance_test_fuel
        CHECK (test_fuel_liters IS NULL OR test_fuel_liters >= 0);
