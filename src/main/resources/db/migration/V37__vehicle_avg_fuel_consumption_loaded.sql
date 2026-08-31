-- ---------------------------------------------------------------------
-- Moyenne de consommation "camion charge", distincte de la moyenne
-- generale existante (avg_fuel_consumption). Calculee a partir des
-- pleins dont la mission rattachee porte un tonnage (cargo_weight_kg)
-- d'au moins fuel.loaded_threshold_percent % de la capacite du camion.
-- Nulle tant qu'aucun tel plein n'existe -- le tonnage reste facultatif
-- a la saisie d'une mission, cette colonne ne l'exige jamais.
-- ---------------------------------------------------------------------

ALTER TABLE vehicles ADD COLUMN avg_fuel_consumption_loaded NUMERIC(6,2);

INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by)
SELECT 'fuel.loaded_threshold_percent', '50', 'INTEGER', 'CARBURANT',
       'Seuil de tonnage (% de la capacite du camion) au-dela duquel un plein est compte comme "charge"', 'system'
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'fuel.loaded_threshold_percent');
