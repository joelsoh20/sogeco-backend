-- ---------------------------------------------------------------------
-- Supprime definitivement le camion de test "TEST-9999" (marque/modele
-- "TEST"/"TEST", jamais utilise -- 0 km, aucun chauffeur, hors service
-- depuis toujours), laisse en base depuis les tout premiers essais de
-- l'application, avant l'adoption de la suppression logique (RG-4.7).
-- Restait visible indefiniment dans la liste des camions malgre son
-- statut "Hors service", contrairement a une desactivation normale.
--
-- Exception deliberee au principe "jamais de suppression physique" :
-- ce n'est pas une donnee metier a historiser, seulement un artefact
-- de test. Idempotent (les DELETE par vehicle_id sont sans effet si
-- la ligne n'existe plus) et sans effet si ce camion n'existe pas ou
-- plus dans cet environnement.
-- ---------------------------------------------------------------------

DO $$
DECLARE
    test_vehicle_id BIGINT;
BEGIN
    SELECT id INTO test_vehicle_id FROM vehicles
    WHERE registration_number = 'TEST-9999' AND active = FALSE;

    IF test_vehicle_id IS NOT NULL THEN
        DELETE FROM gps_positions WHERE vehicle_id = test_vehicle_id;
        DELETE FROM gps_daily_stats WHERE vehicle_id = test_vehicle_id;
        DELETE FROM vehicle_diagnostics WHERE vehicle_id = test_vehicle_id;
        DELETE FROM geofence_events WHERE vehicle_id = test_vehicle_id;
        DELETE FROM alerts WHERE vehicle_id = test_vehicle_id;
        DELETE FROM expenses WHERE vehicle_id = test_vehicle_id;
        DELETE FROM fuel_logs WHERE vehicle_id = test_vehicle_id;
        DELETE FROM maintenance_logs WHERE vehicle_id = test_vehicle_id;
        DELETE FROM vehicle_assignments WHERE vehicle_id = test_vehicle_id;
        DELETE FROM policy_vehicles WHERE vehicle_id = test_vehicle_id;
        DELETE FROM technical_inspections WHERE vehicle_id = test_vehicle_id;
        DELETE FROM claims WHERE vehicle_id = test_vehicle_id;
        DELETE FROM cartes_bleues WHERE vehicle_id = test_vehicle_id;
        DELETE FROM cartes_grises WHERE vehicle_id = test_vehicle_id;
        DELETE FROM mission_automations WHERE vehicle_id = test_vehicle_id;
        DELETE FROM missions WHERE vehicle_id = test_vehicle_id;
        DELETE FROM vehicles WHERE id = test_vehicle_id;
    END IF;
END $$;
