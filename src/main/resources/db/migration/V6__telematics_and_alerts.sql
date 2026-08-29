-- =====================================================================
-- V6 — Telematique, georeperage et alertes
--
-- Tables : webhook_events, gps_positions (partitionnee),
--          vehicle_diagnostics, gps_daily_stats, geofence_zones,
--          vehicle_geofences, geofence_events, alert_rules, alerts,
--          notifications
--
-- gps_positions est la seule table a fort debit du systeme. Elle est
-- partitionnee par mois : la purge des 90 jours devient un DROP de
-- partition, instantane et sans verrou, la ou un DELETE massif
-- bloquerait la table.
-- =====================================================================


-- ---------------------------------------------------------------------
-- JOURNAL BRUT DES TRAMES RECUES
--
-- Toute trame est enregistree AVANT traitement, valide ou non. C'est
-- ce qui permet de diagnostiquer une integration en production sans
-- avoir a rejouer quoi que ce soit.
-- ---------------------------------------------------------------------
CREATE TABLE webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    provider            VARCHAR(40)   NOT NULL,
    device_id           VARCHAR(60),
    payload             JSONB         NOT NULL,
    signature_valid     BOOLEAN       NOT NULL DEFAULT FALSE,
    received_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    status              VARCHAR(20)   NOT NULL DEFAULT 'RECU',
    error_message       VARCHAR(500),

    CONSTRAINT ck_webhook_status CHECK (status IN
        ('RECU', 'TRAITE', 'REJETE', 'DOUBLON', 'APPAREIL_INCONNU'))
);

CREATE INDEX ix_webhook_received ON webhook_events (received_at DESC);
CREATE INDEX ix_webhook_status   ON webhook_events (status, received_at DESC);
CREATE INDEX ix_webhook_device   ON webhook_events (device_id, received_at DESC);


-- ---------------------------------------------------------------------
-- POSITIONS GPS — table partitionnee
--
-- La cle primaire inclut la cle de partitionnement : PostgreSQL
-- l'exige. Cote JPA, seul id est declare comme identifiant, ce qui
-- suffit puisque la sequence est globale.
-- ---------------------------------------------------------------------
CREATE TABLE gps_positions (
    id                  BIGSERIAL,
    vehicle_id          BIGINT        NOT NULL,
    mission_id          BIGINT,
    recorded_at         TIMESTAMPTZ   NOT NULL,

    latitude            NUMERIC(10,7) NOT NULL,
    longitude           NUMERIC(10,7) NOT NULL,
    speed_kmh           NUMERIC(6,2),
    heading             NUMERIC(5,2),
    altitude            NUMERIC(8,2),

    ignition_on         BOOLEAN,
    odometer_km         NUMERIC(12,2),
    fuel_level_percent  NUMERIC(5,2),
    fuel_level_liters   NUMERIC(8,2),

    /* Distance parcourue depuis la position precedente, calculee a
       l'ingestion : evite de recalculer Haversine a chaque lecture. */
    distance_km         NUMERIC(8,3),

    protocol            VARCHAR(40),
    valid               BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_gps_positions PRIMARY KEY (id, recorded_at),
    CONSTRAINT ck_gps_latitude  CHECK (latitude  BETWEEN -90  AND 90),
    CONSTRAINT ck_gps_longitude CHECK (longitude BETWEEN -180 AND 180)
) PARTITION BY RANGE (recorded_at);

CREATE INDEX ix_gps_vehicle_time ON gps_positions (vehicle_id, recorded_at DESC);
CREATE INDEX ix_gps_mission      ON gps_positions (mission_id, recorded_at);

-- Partitions mensuelles pour les 14 prochains mois. Une tache planifiee
-- creera les suivantes le 25 de chaque mois.
DO $$
DECLARE
    start_month DATE := date_trunc('month', now())::DATE - INTERVAL '2 months';
    i INTEGER;
    from_date DATE;
    to_date DATE;
    partition_name TEXT;
BEGIN
    FOR i IN 0..15 LOOP
        from_date := (start_month + (i || ' months')::INTERVAL)::DATE;
        to_date := (from_date + INTERVAL '1 month')::DATE;
        partition_name := 'gps_positions_' || to_char(from_date, 'YYYY_MM');

        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF gps_positions FOR VALUES FROM (%L) TO (%L)',
            partition_name, from_date, to_date);
    END LOOP;
END $$;


-- ---------------------------------------------------------------------
-- FONCTIONS DE GESTION DES PARTITIONS
--
-- La purge des 90 jours se fait par DROP de partition : instantane et
-- sans verrou, la ou un DELETE de plus d'un million de lignes
-- bloquerait la table le temps de son execution.
-- ---------------------------------------------------------------------

CREATE OR REPLACE FUNCTION drop_gps_partitions_before(cutoff DATE)
RETURNS INTEGER AS $$
DECLARE
    partition RECORD;
    dropped INTEGER := 0;
    partition_end DATE;
BEGIN
    FOR partition IN
        SELECT c.relname AS name
        FROM pg_class c
        JOIN pg_inherits i ON i.inhrelid = c.oid
        JOIN pg_class parent ON parent.oid = i.inhparent
        WHERE parent.relname = 'gps_positions'
    LOOP
        -- Le nom porte le mois : gps_positions_2026_08
        partition_end := (to_date(right(partition.name, 7), 'YYYY_MM') + INTERVAL '1 month')::DATE;

        -- On ne supprime qu'une partition ENTIEREMENT anterieure a la
        -- limite : jamais celle qui contient encore des donnees a garder.
        IF partition_end <= cutoff THEN
            EXECUTE format('DROP TABLE IF EXISTS %I', partition.name);
            dropped := dropped + 1;
        END IF;
    END LOOP;

    RETURN dropped;
END;
$$ LANGUAGE plpgsql;


-- Cree la partition d'un mois donne si elle n'existe pas encore.
-- Appelee chaque mois par la tache planifiee, en anticipation.
CREATE OR REPLACE FUNCTION ensure_gps_partition(target DATE)
RETURNS TEXT AS $$
DECLARE
    from_date DATE := date_trunc('month', target)::DATE;
    to_date DATE := (date_trunc('month', target) + INTERVAL '1 month')::DATE;
    partition_name TEXT := 'gps_positions_' || to_char(from_date, 'YYYY_MM');
BEGIN
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS %I PARTITION OF gps_positions FOR VALUES FROM (%L) TO (%L)',
        partition_name, from_date, to_date);
    RETURN partition_name;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------
-- DIAGNOSTICS MOTEUR
--
-- Alimentee seulement si le boitier lit le bus CAN / OBD-II. Table
-- separee des positions : ces donnees arrivent moins souvent et
-- n'auraient aucun sens repliquees a chaque trame.
-- ---------------------------------------------------------------------
CREATE TABLE vehicle_diagnostics (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT        NOT NULL,
    recorded_at         TIMESTAMPTZ   NOT NULL,
    engine_temperature  NUMERIC(6,2),
    engine_rpm          INTEGER,
    battery_voltage     NUMERIC(6,2),
    engine_hours        NUMERIC(10,2),
    error_codes         JSONB,
    dtc_count           INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_diagnostics_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
);

CREATE INDEX ix_diagnostics_vehicle ON vehicle_diagnostics (vehicle_id, recorded_at DESC);
CREATE INDEX ix_diagnostics_dtc     ON vehicle_diagnostics (dtc_count) WHERE dtc_count > 0;


-- ---------------------------------------------------------------------
-- AGREGATS JOURNALIERS
--
-- Conserves au-dela des 90 jours de retention. Sans eux, toute analyse
-- historique de kilometrage serait perdue a la purge.
-- Volumetrie negligeable : 11 camions x 365 jours par an.
-- ---------------------------------------------------------------------
CREATE TABLE gps_daily_stats (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT        NOT NULL,
    stat_date           DATE          NOT NULL,
    distance_km         NUMERIC(10,2) NOT NULL DEFAULT 0,
    max_speed_kmh       NUMERIC(6,2),
    avg_speed_kmh       NUMERIC(6,2),
    driving_minutes     INTEGER       NOT NULL DEFAULT 0,
    idle_minutes        INTEGER       NOT NULL DEFAULT 0,
    position_count      INTEGER       NOT NULL DEFAULT 0,
    first_position_at   TIMESTAMPTZ,
    last_position_at    TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_daily_stats UNIQUE (vehicle_id, stat_date),
    CONSTRAINT fk_daily_stats_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id)
);

CREATE INDEX ix_daily_stats_date ON gps_daily_stats (stat_date DESC);


-- ---------------------------------------------------------------------
-- GEOREPERAGE
-- ---------------------------------------------------------------------
CREATE TABLE geofence_zones (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(120)  NOT NULL,
    zone_type           VARCHAR(20)   NOT NULL,
    polygon_geojson     JSONB         NOT NULL,
    city_id             BIGINT,
    alert_on_entry      BOOLEAN       NOT NULL DEFAULT FALSE,
    alert_on_exit       BOOLEAN       NOT NULL DEFAULT TRUE,
    description         VARCHAR(255),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT fk_zones_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_zones_type CHECK (zone_type IN ('AUTORISEE', 'INTERDITE', 'CLIENT', 'AGENCE'))
);

-- Zone applicable a certains camions seulement. Sans ligne, la zone
-- s'applique a tout le parc.
CREATE TABLE vehicle_geofences (
    vehicle_id          BIGINT NOT NULL,
    geofence_zone_id    BIGINT NOT NULL,

    CONSTRAINT pk_vehicle_geofences PRIMARY KEY (vehicle_id, geofence_zone_id),
    CONSTRAINT fk_vg_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_vg_zone    FOREIGN KEY (geofence_zone_id) REFERENCES geofence_zones (id) ON DELETE CASCADE
);

CREATE TABLE geofence_events (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT        NOT NULL,
    geofence_zone_id    BIGINT        NOT NULL,
    event_type          VARCHAR(20)   NOT NULL,
    occurred_at         TIMESTAMPTZ   NOT NULL,
    latitude            NUMERIC(10,7),
    longitude           NUMERIC(10,7),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_ge_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_ge_zone    FOREIGN KEY (geofence_zone_id) REFERENCES geofence_zones (id),
    CONSTRAINT ck_ge_type CHECK (event_type IN ('ENTREE', 'SORTIE'))
);

CREATE INDEX ix_ge_vehicle ON geofence_events (vehicle_id, occurred_at DESC);


-- ---------------------------------------------------------------------
-- REGLES D'ALERTE
--
-- Aucun seuil n'est code en dur (RG-10.3) : la vitesse maximale, la
-- temperature moteur ou le niveau de carburant bas se pilotent depuis
-- l'interface.
-- ---------------------------------------------------------------------
CREATE TABLE alert_rules (
    id                  BIGSERIAL PRIMARY KEY,
    alert_type          VARCHAR(40)   NOT NULL,
    label               VARCHAR(150)  NOT NULL,
    threshold_value     NUMERIC(12,2),
    comparison_operator VARCHAR(10),
    level               VARCHAR(20)   NOT NULL,
    cooldown_minutes    INTEGER       NOT NULL DEFAULT 30,
    notify_role_codes   VARCHAR(255),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_alert_rules_type UNIQUE (alert_type),
    CONSTRAINT ck_alert_rules_level CHECK (level IN ('CRITIQUE', 'IMPORTANT', 'MINEUR', 'INFORMATION')),
    CONSTRAINT ck_alert_rules_operator CHECK (comparison_operator IS NULL
        OR comparison_operator IN ('GT', 'GTE', 'LT', 'LTE', 'EQ'))
);


-- ---------------------------------------------------------------------
-- ALERTES
--
-- occurrences absorbe les repetitions : une meme anomalie dans la
-- fenetre de cooldown incremente le compteur au lieu de creer une
-- nouvelle ligne. Trop d'alertes tuent l'alerte (RG-10.4).
-- ---------------------------------------------------------------------
CREATE TABLE alerts (
    id                      BIGSERIAL PRIMARY KEY,
    alert_rule_id           BIGINT,
    vehicle_id              BIGINT,
    driver_id               BIGINT,
    mission_id              BIGINT,
    geofence_zone_id        BIGINT,

    alert_type              VARCHAR(40)   NOT NULL,
    level                   VARCHAR(20)   NOT NULL,
    title                   VARCHAR(150)  NOT NULL,
    description             VARCHAR(500),

    triggered_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    latitude                NUMERIC(10,7),
    longitude               NUMERIC(10,7),
    location_label          VARCHAR(255),

    status                  VARCHAR(20)   NOT NULL DEFAULT 'NON_RESOLUE',
    occurrences             INTEGER       NOT NULL DEFAULT 1,
    last_occurrence_at      TIMESTAMPTZ,

    acknowledged_by_user_id BIGINT,
    acknowledged_at         TIMESTAMPTZ,
    resolved_by_user_id     BIGINT,
    resolved_at             TIMESTAMPTZ,
    resolution_note         VARCHAR(500),

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_alerts_rule    FOREIGN KEY (alert_rule_id)    REFERENCES alert_rules (id),
    CONSTRAINT fk_alerts_vehicle FOREIGN KEY (vehicle_id)       REFERENCES vehicles (id),
    CONSTRAINT fk_alerts_driver  FOREIGN KEY (driver_id)        REFERENCES drivers (id),
    CONSTRAINT fk_alerts_mission FOREIGN KEY (mission_id)       REFERENCES missions (id),
    CONSTRAINT fk_alerts_zone    FOREIGN KEY (geofence_zone_id) REFERENCES geofence_zones (id),
    CONSTRAINT fk_alerts_ack     FOREIGN KEY (acknowledged_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_alerts_res     FOREIGN KEY (resolved_by_user_id)     REFERENCES users (id),

    CONSTRAINT ck_alerts_level  CHECK (level IN ('CRITIQUE', 'IMPORTANT', 'MINEUR', 'INFORMATION')),
    CONSTRAINT ck_alerts_status CHECK (status IN ('NON_RESOLUE', 'EN_COURS', 'RESOLUE', 'IGNOREE')),
    -- Une alerte resolue porte toujours son auteur et sa date
    CONSTRAINT ck_alerts_resolution CHECK (
        status <> 'RESOLUE' OR (resolved_at IS NOT NULL AND resolved_by_user_id IS NOT NULL))
);

CREATE INDEX ix_alerts_status   ON alerts (status, level, triggered_at DESC);
CREATE INDEX ix_alerts_vehicle  ON alerts (vehicle_id, triggered_at DESC);
CREATE INDEX ix_alerts_type     ON alerts (alert_type, triggered_at DESC);
CREATE INDEX ix_alerts_open     ON alerts (triggered_at DESC) WHERE status IN ('NON_RESOLUE', 'EN_COURS');


-- ---------------------------------------------------------------------
-- NOTIFICATIONS
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT        NOT NULL,
    alert_id            BIGINT,
    channel             VARCHAR(20)   NOT NULL DEFAULT 'IN_APP',
    title               VARCHAR(150)  NOT NULL,
    message             VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    sent_at             TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_notifications_user  FOREIGN KEY (user_id)  REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_alert FOREIGN KEY (alert_id) REFERENCES alerts (id),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS')),
    CONSTRAINT ck_notifications_status  CHECK (status IN ('EN_ATTENTE', 'ENVOYEE', 'ECHEC'))
);

CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;


-- ---------------------------------------------------------------------
-- LIEN ALERTE -> INTERVENTION
-- Le bouton "Creer une intervention" de l'ecran Alertes (RG-7.8).
-- ---------------------------------------------------------------------
ALTER TABLE maintenance_logs ADD COLUMN alert_id BIGINT;
ALTER TABLE maintenance_logs ADD CONSTRAINT fk_maintenance_alert
    FOREIGN KEY (alert_id) REFERENCES alerts (id);


-- ---------------------------------------------------------------------
-- REGLES PAR DEFAUT
--
-- Volontairement restreintes au demarrage : quatre regles suffisent.
-- Trop d'alertes des le premier jour et plus personne ne les lit.
-- Les autres sont creees inactives, a activer apres calibrage.
-- ---------------------------------------------------------------------
INSERT INTO alert_rules (alert_type, label, threshold_value, comparison_operator,
                         level, cooldown_minutes, notify_role_codes, active, created_by) VALUES
    ('VITESSE_EXCESSIVE',        'Vitesse excessive',                   90,   'GT',  'IMPORTANT', 30,
        'ROLE_GESTIONNAIRE,ROLE_SUPERVISEUR', TRUE,  'system'),
    ('CARBURANT_BAS',            'Niveau de carburant bas',             20,   'LT',  'IMPORTANT', 60,
        'ROLE_GESTIONNAIRE,ROLE_AGENT_FLOTTE', TRUE, 'system'),
    ('PERTE_SIGNAL',             'Perte de signal en mission',          30,   'GT',  'IMPORTANT', 60,
        'ROLE_SUPERVISEUR', TRUE,  'system'),
    ('MAINTENANCE_ECHUE',        'Echeance de maintenance depassee',    NULL, NULL,  'IMPORTANT', 1440,
        'ROLE_GESTIONNAIRE', TRUE, 'system'),

    -- Activables apres la periode de fiabilisation
    ('TEMPERATURE_MOTEUR',       'Temperature moteur excessive',        100,  'GT',  'CRITIQUE',  30,
        'ROLE_GESTIONNAIRE', FALSE, 'system'),
    ('PANNE_DETECTEE',           'Code defaut moteur remonte',          NULL, NULL,  'CRITIQUE',  60,
        'ROLE_GESTIONNAIRE', FALSE, 'system'),
    ('SIPHONNAGE',               'Chute anormale du niveau de carburant', 15, 'GT',  'CRITIQUE',  30,
        'ROLE_GESTIONNAIRE,ROLE_DIRECTION', FALSE, 'system'),
    ('SURCONSOMMATION',          'Surconsommation constatee',           20,   'GT',  'IMPORTANT', 720,
        'ROLE_GESTIONNAIRE', FALSE, 'system'),
    ('DEVIATION_ITINERAIRE',     'Sortie du corridor de reference',     5,    'GT',  'CRITIQUE',  30,
        'ROLE_SUPERVISEUR', FALSE, 'system'),
    ('GEOREPERAGE',              'Entree ou sortie de zone',            NULL, NULL,  'IMPORTANT', 30,
        'ROLE_SUPERVISEUR', FALSE, 'system'),
    ('DEMARRAGE_NON_AUTORISE',   'Demarrage hors mission planifiee',    NULL, NULL,  'CRITIQUE',  60,
        'ROLE_GESTIONNAIRE,ROLE_DIRECTION', FALSE, 'system'),
    ('ASSURANCE_ECHEANCE',       'Assurance a echeance ou expiree',     30,   'LTE', 'CRITIQUE',  1440,
        'ROLE_GESTIONNAIRE,ROLE_COMPTABLE', TRUE, 'system'),
    ('VISITE_TECHNIQUE_ECHEANCE','Visite technique a echeance',         30,   'LTE', 'IMPORTANT', 1440,
        'ROLE_GESTIONNAIRE', TRUE, 'system'),
    ('PERMIS_ECHEANCE',          'Permis de conduire a echeance',       30,   'LTE', 'MINEUR',    1440,
        'ROLE_GESTIONNAIRE', TRUE, 'system'),
    ('PEAGE_IMPAYE',             'Peage impaye',                        NULL, NULL,  'MINEUR',    1440,
        'ROLE_COMPTABLE', FALSE, 'system');


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('telematics.webhook_secret',      'CHANGEZ_CE_SECRET_PARTAGE_SOGECO_2026', 'STRING', 'TELEMATIQUE',
        'Secret partage du webhook telematique',                                    'system'),
    ('telematics.max_clock_skew_min',  '10',   'INTEGER', 'TELEMATIQUE', 'Derive d''horloge toleree (minutes)',        'system'),
    ('telematics.max_speed_kmh',       '160',  'INTEGER', 'TELEMATIQUE', 'Vitesse au-dela de laquelle la trame est rejetee', 'system'),
    ('telematics.batch_size',          '100',  'INTEGER', 'TELEMATIQUE', 'Taille des lots d''ecriture des positions',  'system'),
    ('telematics.broadcast_min_sec',   '10',   'INTEGER', 'TELEMATIQUE', 'Intervalle minimal de diffusion WebSocket (s)', 'system'),
    ('telematics.idle_speed_kmh',      '3',    'INTEGER', 'TELEMATIQUE', 'Vitesse en deca de laquelle le camion est a l''arret', 'system');
