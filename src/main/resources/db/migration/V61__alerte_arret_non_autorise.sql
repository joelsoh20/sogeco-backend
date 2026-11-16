-- Camion en mission immobile plus de 15 min hors des points de depart et d'arrivee (rayon 50 m).
-- Seuil (minutes) = threshold_value ; le rayon se regle par le parametre alerts.stop_radius_meters.
INSERT INTO alert_rules (alert_type, label, threshold_value, comparison_operator,
                         level, cooldown_minutes, notify_role_codes, active, created_by) VALUES
    ('ARRET_NON_AUTORISE', 'Arret prolonge en mission hors point de depart ou de livraison', 15, NULL,
        'CRITIQUE', 30, 'ROLE_GESTIONNAIRE,ROLE_ADMIN', TRUE, 'system')
ON CONFLICT (alert_type) DO NOTHING;
