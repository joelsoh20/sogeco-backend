-- Notification pour tout document expire ou manquant de l'ecran Assurance & Visite technique.
-- Une alerte par camion liste ses documents concernes (voir ComplianceDocumentAlerts). Niveau et
-- destinataires restent reglables depuis l'ecran Parametres.
INSERT INTO alert_rules (alert_type, label, threshold_value, comparison_operator,
                         level, cooldown_minutes, notify_role_codes, active, created_by) VALUES
    ('DOCUMENT_EXPIRE',   'Document expire (assurance, visite technique, cartes)', NULL, NULL,
        'CRITIQUE',  1440, 'ROLE_GESTIONNAIRE,ROLE_COMPTABLE', TRUE, 'system'),
    ('DOCUMENT_MANQUANT', 'Document manquant pour un camion',                      NULL, NULL,
        'IMPORTANT', 1440, 'ROLE_GESTIONNAIRE,ROLE_COMPTABLE', TRUE, 'system')
ON CONFLICT (alert_type) DO NOTHING;
