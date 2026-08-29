-- =====================================================================
-- V2 — Initialisation technique
--
-- Conformement a la decision D12, AUCUNE donnee metier n'est injectee
-- ici : ni camions, ni chauffeurs, ni sites, ni clients. Ces referentiels
-- sont saisis via les formulaires de l'application.
--
-- Seuls sont initialises les elements techniques sans lesquels
-- l'application ne peut pas demarrer :
--   1. le referentiel de permissions
--   2. les sept roles systeme et leurs permissions
--   3. le compte administrateur initial
--   4. les parametres systeme par defaut
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. PERMISSIONS
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, module, label, created_by) VALUES
    -- Tableaux de bord
    ('DASHBOARD_OPERATIONAL_READ', 'DASHBOARD',   'Consulter le tableau de bord operationnel', 'system'),
    ('DASHBOARD_EXECUTIVE_READ',   'DASHBOARD',   'Consulter le tableau de bord de direction', 'system'),

    -- Suivi telematique
    ('TRACKING_READ',              'TRACKING',    'Consulter la carte temps reel',             'system'),
    ('TRACKING_HISTORY_READ',      'TRACKING',    'Consulter l''historique des trajets',       'system'),

    -- Parc
    ('VEHICLE_READ',               'VEHICLE',     'Consulter les camions',                     'system'),
    ('VEHICLE_CREATE',             'VEHICLE',     'Creer un camion',                           'system'),
    ('VEHICLE_UPDATE',             'VEHICLE',     'Modifier un camion',                        'system'),
    ('VEHICLE_DELETE',             'VEHICLE',     'Desactiver un camion',                      'system'),

    -- Chauffeurs
    ('DRIVER_READ',                'DRIVER',      'Consulter les chauffeurs',                  'system'),
    ('DRIVER_CREATE',              'DRIVER',      'Creer un chauffeur',                        'system'),
    ('DRIVER_UPDATE',              'DRIVER',      'Modifier un chauffeur',                     'system'),
    ('DRIVER_DELETE',              'DRIVER',      'Desactiver un chauffeur',                   'system'),
    ('DRIVER_RATE',                'DRIVER',      'Evaluer un chauffeur',                      'system'),
    ('DRIVER_BONUS_MANAGE',        'DRIVER',      'Attribuer et valider les primes',           'system'),

    -- Missions
    ('MISSION_READ',               'MISSION',     'Consulter les missions',                    'system'),
    ('MISSION_CREATE',             'MISSION',     'Creer une mission',                         'system'),
    ('MISSION_UPDATE',             'MISSION',     'Modifier et cloturer une mission',          'system'),
    ('MISSION_CANCEL',             'MISSION',     'Annuler une mission',                       'system'),

    -- Carburant
    ('FUEL_READ',                  'FUEL',        'Consulter les ravitaillements',             'system'),
    ('FUEL_CREATE',                'FUEL',        'Saisir un plein',                           'system'),
    ('FUEL_UPDATE',                'FUEL',        'Corriger un plein',                         'system'),

    -- Maintenance
    ('MAINTENANCE_READ',           'MAINTENANCE', 'Consulter les interventions',               'system'),
    ('MAINTENANCE_CREATE',         'MAINTENANCE', 'Creer une intervention',                    'system'),
    ('MAINTENANCE_UPDATE',         'MAINTENANCE', 'Modifier une intervention',                 'system'),

    -- Conformite
    ('INSURANCE_READ',             'INSURANCE',   'Consulter assurances et visites',           'system'),
    ('INSURANCE_CREATE',           'INSURANCE',   'Creer un contrat ou une visite',            'system'),
    ('INSURANCE_UPDATE',           'INSURANCE',   'Modifier un contrat ou une visite',         'system'),

    -- Alertes
    ('ALERT_READ',                 'ALERT',       'Consulter les alertes',                     'system'),
    ('ALERT_ACKNOWLEDGE',          'ALERT',       'Prendre en compte une alerte',              'system'),
    ('ALERT_RESOLVE',              'ALERT',       'Resoudre une alerte',                       'system'),
    ('ALERT_RULE_MANAGE',          'ALERT',       'Parametrer les regles d''alerte',           'system'),

    -- Georeperage
    ('GEOFENCE_READ',              'GEOFENCE',    'Consulter les zones',                       'system'),
    ('GEOFENCE_MANAGE',            'GEOFENCE',    'Creer et modifier les zones',               'system'),

    -- Commercial
    ('CLIENT_READ',                'CLIENT',      'Consulter les clients',                     'system'),
    ('CLIENT_MANAGE',              'CLIENT',      'Creer et modifier les clients',             'system'),
    ('TARIFF_MANAGE',              'CLIENT',      'Gerer la grille tarifaire',                 'system'),

    -- Partenaires
    ('PARTNER_READ',               'PARTNER',     'Consulter les partenaires',                 'system'),
    ('PARTNER_MANAGE',             'PARTNER',     'Creer et modifier les partenaires',         'system'),

    -- Rapports
    ('REPORT_READ',                'REPORT',      'Consulter les rapports',                    'system'),
    ('REPORT_EXPORT',              'REPORT',      'Exporter les rapports',                     'system'),

    -- Donnees sensibles
    ('FINANCE_READ',               'FINANCE',     'Consulter les donnees financieres',         'system'),
    ('SALARY_READ',                'FINANCE',     'Consulter salaires et primes',              'system'),

    -- Administration
    ('USER_MANAGE',                'ADMIN',       'Gerer les utilisateurs',                    'system'),
    ('ROLE_MANAGE',                'ADMIN',       'Gerer les roles et permissions',            'system'),
    ('AGENCY_MANAGE',              'ADMIN',       'Gerer les sites',                           'system'),
    ('CITY_MANAGE',                'ADMIN',       'Gerer les villes',                          'system'),
    ('SETTING_MANAGE',             'ADMIN',       'Gerer les parametres systeme',              'system'),
    ('INTEGRATION_MANAGE',         'ADMIN',       'Gerer les integrations',                    'system'),
    ('AUDIT_READ',                 'ADMIN',       'Consulter le journal d''audit',             'system'),

    -- Acces restreint aux donnees personnelles (role chauffeur)
    ('SELF_READ',                  'SELF',        'Consulter ses propres informations',        'system');


-- ---------------------------------------------------------------------
-- 2. ROLES SYSTEME
-- ---------------------------------------------------------------------
INSERT INTO roles (code, label, description, is_system, created_by) VALUES
    ('ROLE_ADMIN',         'Administrateur',     'Acces complet a toutes les fonctionnalites',    TRUE, 'system'),
    ('ROLE_DIRECTION',     'Direction Generale', 'Consultation des statistiques et rapports',     TRUE, 'system'),
    ('ROLE_GESTIONNAIRE',  'Gestionnaire',       'Gestion des camions et des missions',           TRUE, 'system'),
    ('ROLE_SUPERVISEUR',   'Superviseur',        'Suivi des missions et des alertes',             TRUE, 'system'),
    ('ROLE_COMPTABLE',     'Comptable',          'Gestion financiere',                            TRUE, 'system'),
    ('ROLE_AGENT_FLOTTE',  'Agent de flotte',    'Gestion operationnelle de la flotte',           TRUE, 'system'),
    ('ROLE_CHAUFFEUR',     'Chauffeur',          'Consultation limitee de ses propres donnees',   TRUE, 'system');


-- ---------------------------------------------------------------------
-- 3. AFFECTATION DES PERMISSIONS
-- ---------------------------------------------------------------------

-- Administrateur : toutes les permissions.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ROLE_ADMIN';

-- Direction Generale : lecture globale, y compris financiere.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_DIRECTION' AND p.code IN (
    'DASHBOARD_OPERATIONAL_READ', 'DASHBOARD_EXECUTIVE_READ',
    'TRACKING_READ', 'TRACKING_HISTORY_READ',
    'VEHICLE_READ', 'DRIVER_READ', 'MISSION_READ', 'FUEL_READ',
    'MAINTENANCE_READ', 'INSURANCE_READ', 'ALERT_READ', 'GEOFENCE_READ',
    'CLIENT_READ', 'PARTNER_READ',
    'REPORT_READ', 'REPORT_EXPORT',
    'FINANCE_READ', 'SALARY_READ', 'AUDIT_READ'
);

-- Gestionnaire : exploitation complete, sans acces financier consolide.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_GESTIONNAIRE' AND p.code IN (
    'DASHBOARD_OPERATIONAL_READ',
    'TRACKING_READ', 'TRACKING_HISTORY_READ',
    'VEHICLE_READ', 'VEHICLE_CREATE', 'VEHICLE_UPDATE', 'VEHICLE_DELETE',
    'DRIVER_READ', 'DRIVER_CREATE', 'DRIVER_UPDATE', 'DRIVER_DELETE', 'DRIVER_RATE',
    'MISSION_READ', 'MISSION_CREATE', 'MISSION_UPDATE', 'MISSION_CANCEL',
    'FUEL_READ', 'FUEL_CREATE', 'FUEL_UPDATE',
    'MAINTENANCE_READ', 'MAINTENANCE_CREATE', 'MAINTENANCE_UPDATE',
    'INSURANCE_READ', 'INSURANCE_CREATE', 'INSURANCE_UPDATE',
    'ALERT_READ', 'ALERT_ACKNOWLEDGE', 'ALERT_RESOLVE',
    'GEOFENCE_READ', 'CLIENT_READ', 'PARTNER_READ', 'PARTNER_MANAGE',
    'REPORT_READ'
);

-- Superviseur : suivi et traitement des alertes.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_SUPERVISEUR' AND p.code IN (
    'DASHBOARD_OPERATIONAL_READ',
    'TRACKING_READ', 'TRACKING_HISTORY_READ',
    'VEHICLE_READ', 'DRIVER_READ',
    'MISSION_READ', 'MISSION_UPDATE',
    'FUEL_READ', 'MAINTENANCE_READ', 'INSURANCE_READ',
    'ALERT_READ', 'ALERT_ACKNOWLEDGE', 'ALERT_RESOLVE',
    'GEOFENCE_READ', 'CLIENT_READ'
);

-- Comptable : donnees financieres et conformite.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_COMPTABLE' AND p.code IN (
    'VEHICLE_READ', 'DRIVER_READ', 'MISSION_READ',
    'FUEL_READ', 'FUEL_UPDATE',
    'MAINTENANCE_READ',
    'INSURANCE_READ', 'INSURANCE_CREATE', 'INSURANCE_UPDATE',
    'CLIENT_READ', 'CLIENT_MANAGE', 'TARIFF_MANAGE',
    'PARTNER_READ',
    'REPORT_READ', 'REPORT_EXPORT',
    'FINANCE_READ', 'SALARY_READ', 'DRIVER_BONUS_MANAGE'
);

-- Agent de flotte : operationnel terrain.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_AGENT_FLOTTE' AND p.code IN (
    'DASHBOARD_OPERATIONAL_READ',
    'TRACKING_READ',
    'VEHICLE_READ', 'VEHICLE_UPDATE',
    'DRIVER_READ',
    'MISSION_READ', 'MISSION_CREATE', 'MISSION_UPDATE',
    'FUEL_READ', 'FUEL_CREATE',
    'MAINTENANCE_READ', 'MAINTENANCE_CREATE',
    'INSURANCE_READ',
    'ALERT_READ', 'ALERT_ACKNOWLEDGE',
    'GEOFENCE_READ', 'PARTNER_READ'
);

-- Chauffeur : ses propres donnees uniquement.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ROLE_CHAUFFEUR' AND p.code = 'SELF_READ';


-- ---------------------------------------------------------------------
-- 4. COMPTE ADMINISTRATEUR INITIAL
--
-- Identifiant : admin@sogeco.cm
-- Mot de passe : Sogeco@2026
--
-- must_change_password = TRUE : le changement est impose a la premiere
-- connexion. Ce compte n'est rattache a aucun site tant que le
-- referentiel n'est pas saisi.
-- ---------------------------------------------------------------------
INSERT INTO users (email, password_hash, first_name, last_name, status, must_change_password, created_by)
VALUES (
    'admin@sogeco.cm',
    '$2b$12$EttNGldCg3lWpfk6BdZckugZyBc9qT1BYM3t5bSaerIwh3T1IzI8K',
    'Administrateur',
    'SOGECO',
    'ACTIF',
    TRUE,
    'system'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@sogeco.cm' AND r.code = 'ROLE_ADMIN';


-- ---------------------------------------------------------------------
-- 5. PARAMETRES SYSTEME PAR DEFAUT
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('company.name',                    'SOGECO Sarl',    'STRING',  'ENTREPRISE', 'Raison sociale',                              'system'),
    ('company.currency',                'XAF',            'STRING',  'ENTREPRISE', 'Devise',                                      'system'),
    ('company.timezone',                'Africa/Douala',  'STRING',  'ENTREPRISE', 'Fuseau horaire',                              'system'),
    ('dashboard.director_message',      '',               'STRING',  'DASHBOARD',  'Message de la Direction',                      'system'),

    ('security.max_failed_attempts',    '5',              'INTEGER', 'SECURITE',   'Echecs avant verrouillage',                   'system'),
    ('security.lock_duration_minutes',  '15',             'INTEGER', 'SECURITE',   'Duree du verrouillage (minutes)',             'system'),
    ('security.totp_required_roles',    'ROLE_ADMIN,ROLE_DIRECTION,ROLE_COMPTABLE', 'STRING', 'SECURITE', 'Roles avec 2FA obligatoire', 'system'),

    ('alert.speed_limit_kmh',           '90',             'INTEGER', 'ALERTE',     'Seuil de vitesse excessive (km/h)',           'system'),
    ('alert.engine_temp_max',           '100',            'INTEGER', 'ALERTE',     'Temperature moteur maximale (C)',             'system'),
    ('alert.fuel_low_percent',          '20',             'INTEGER', 'ALERTE',     'Seuil de carburant bas (%)',                  'system'),
    ('alert.overconsumption_percent',   '20',             'INTEGER', 'ALERTE',     'Ecart de surconsommation (%)',                'system'),
    ('alert.route_deviation_km',        '5',              'INTEGER', 'ALERTE',     'Tolerance de deviation d''itineraire (km)',   'system'),
    ('alert.cooldown_minutes',          '30',             'INTEGER', 'ALERTE',     'Delai anti-repetition des alertes (minutes)', 'system'),
    ('alert.expiry_warning_days',       '30',             'INTEGER', 'ALERTE',     'Preavis d''echeance documentaire (jours)',    'system'),

    ('gps.retention_days',              '90',             'INTEGER', 'TELEMATIQUE','Conservation des positions (jours)',          'system'),
    ('gps.offline_threshold_minutes',   '30',             'INTEGER', 'TELEMATIQUE','Delai avant statut hors ligne (minutes)',     'system'),

    ('performance.weight_safety',       '25',             'INTEGER', 'PERFORMANCE','Ponderation conduite securisee (%)',          'system'),
    ('performance.weight_fuel',         '20',             'INTEGER', 'PERFORMANCE','Ponderation consommation economique (%)',     'system'),
    ('performance.weight_punctuality',  '25',             'INTEGER', 'PERFORMANCE','Ponderation respect des delais (%)',          'system'),
    ('performance.weight_vehicle_care', '15',             'INTEGER', 'PERFORMANCE','Ponderation entretien du vehicule (%)',       'system'),
    ('performance.weight_compliance',   '15',             'INTEGER', 'PERFORMANCE','Ponderation respect des regles (%)',          'system'),
    ('performance.min_missions',        '3',              'INTEGER', 'PERFORMANCE','Missions minimales pour noter un chauffeur',  'system');
