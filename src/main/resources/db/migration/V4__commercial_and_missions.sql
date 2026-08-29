-- =====================================================================
-- V4 — Commercial et missions
--
-- Tables : clients, service_types, routes, tariffs,
--          missions, mission_waypoints
--
-- Rappel de la decision D1/D12 : SOGECO facture le transport a ses
-- clients. La mission porte donc un chiffre d'affaires et une marge.
-- =====================================================================


-- ---------------------------------------------------------------------
-- CLIENTS
-- Donneurs d'ordre : Socpalm, Nestle, Dangote, Tradex, Bollore...
-- ---------------------------------------------------------------------
CREATE TABLE clients (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20)   NOT NULL,
    company_name        VARCHAR(150)  NOT NULL,
    contact_name        VARCHAR(120),
    phone               VARCHAR(30),
    email               VARCHAR(150),
    address             VARCHAR(255),
    city_id             BIGINT,
    tax_number          VARCHAR(50),
    payment_terms_days  INTEGER       NOT NULL DEFAULT 30,
    notes               VARCHAR(500),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_clients_code UNIQUE (code),
    CONSTRAINT fk_clients_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_clients_payment_terms CHECK (payment_terms_days >= 0)
);

CREATE INDEX ix_clients_active ON clients (active);
CREATE INDEX ix_clients_name   ON clients (company_name);


-- ---------------------------------------------------------------------
-- TYPES DE PRESTATION
-- Liste fermee et courte, initialisee ci-dessous : c'est un referentiel
-- technique et non des donnees metier au sens de la decision D12.
-- ---------------------------------------------------------------------
CREATE TABLE service_types (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(40)   NOT NULL,
    label               VARCHAR(120)  NOT NULL,
    description         VARCHAR(255),
    billable            BOOLEAN       NOT NULL DEFAULT TRUE,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_service_types_code UNIQUE (code)
);

INSERT INTO service_types (code, label, description, billable, created_by) VALUES
    ('LIVRAISON_CLIENT',    'Livraison client',
        'Acheminement facture a un client',                       TRUE,  'system'),
    ('REAPPROVISIONNEMENT', 'Reapprovisionnement',
        'Transfert vers une agence ou un depot SOGECO',           FALSE, 'system'),
    ('TRANSFERT',           'Transfert inter-agences',
        'Repositionnement de marchandises entre implantations',   FALSE, 'system');


-- ---------------------------------------------------------------------
-- CORRIDORS DE REFERENCE
--
-- Limites aux liaisons entre villes d'implantation. Les distances et
-- consommations sont RELEVEES SUR LES TRAJETS REELS, pas estimees par
-- un calculateur routier : l'etat des axes rend les estimations
-- theoriques peu fiables (decision D13).
-- ---------------------------------------------------------------------
CREATE TABLE routes (
    id                          BIGSERIAL PRIMARY KEY,
    origin_city_id              BIGINT        NOT NULL,
    destination_city_id         BIGINT        NOT NULL,
    label                       VARCHAR(120)  NOT NULL,
    reference_distance_km       NUMERIC(10,2),
    reference_duration_minutes  INTEGER,
    reference_fuel_liters       NUMERIC(8,2),
    corridor_geojson            JSONB,
    tolerance_km                NUMERIC(6,2)  NOT NULL DEFAULT 5,
    active                      BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at                  TIMESTAMPTZ,

    version                     INTEGER       NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    created_by                  VARCHAR(150),
    updated_by                  VARCHAR(150),

    CONSTRAINT uk_routes_cities UNIQUE (origin_city_id, destination_city_id),
    CONSTRAINT fk_routes_origin      FOREIGN KEY (origin_city_id)      REFERENCES cities (id),
    CONSTRAINT fk_routes_destination FOREIGN KEY (destination_city_id) REFERENCES cities (id),
    CONSTRAINT ck_routes_distinct_cities CHECK (origin_city_id <> destination_city_id),
    CONSTRAINT ck_routes_distance CHECK (reference_distance_km IS NULL OR reference_distance_km > 0)
);


-- ---------------------------------------------------------------------
-- GRILLE TARIFAIRE
--
-- client_id nul = tarif general applicable a defaut de tarif negocie.
-- route_id nul  = tarif applicable quel que soit le trajet.
-- La resolution retient le tarif le plus specifique (voir TariffService).
-- ---------------------------------------------------------------------
CREATE TABLE tariffs (
    id                  BIGSERIAL PRIMARY KEY,
    client_id           BIGINT,
    service_type_id     BIGINT        NOT NULL,
    route_id            BIGINT,
    pricing_mode        VARCHAR(20)   NOT NULL,
    unit_price          NUMERIC(15,2) NOT NULL,
    min_amount          NUMERIC(15,2),
    valid_from          DATE          NOT NULL,
    valid_to            DATE,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT fk_tariffs_client       FOREIGN KEY (client_id)       REFERENCES clients (id),
    CONSTRAINT fk_tariffs_service_type FOREIGN KEY (service_type_id) REFERENCES service_types (id),
    CONSTRAINT fk_tariffs_route        FOREIGN KEY (route_id)        REFERENCES routes (id),
    CONSTRAINT ck_tariffs_mode CHECK (pricing_mode IN ('FORFAIT', 'PAR_KM', 'PAR_TONNE')),
    CONSTRAINT ck_tariffs_price CHECK (unit_price >= 0),
    CONSTRAINT ck_tariffs_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_tariffs_lookup ON tariffs (client_id, service_type_id, route_id, active);


-- ---------------------------------------------------------------------
-- MISSIONS
--
-- Unite d'analyse centrale de l'application : sans mission correctement
-- cloturee, ni la rentabilite, ni la performance des chauffeurs, ni les
-- couts par corridor ne sont calculables.
-- ---------------------------------------------------------------------
CREATE TABLE missions (
    id                      BIGSERIAL PRIMARY KEY,
    mission_number          VARCHAR(30)   NOT NULL,

    -- Rattachements
    client_id               BIGINT,
    service_type_id         BIGINT        NOT NULL,
    vehicle_id              BIGINT        NOT NULL,
    driver_id               BIGINT        NOT NULL,
    agency_id               BIGINT,
    route_id                BIGINT,
    origin_city_id          BIGINT,
    destination_city_id     BIGINT,

    -- Trajet
    departure_address       VARCHAR(255),
    destination_address     VARCHAR(255),
    departure_latitude      NUMERIC(10,7),
    departure_longitude     NUMERIC(10,7),
    destination_latitude    NUMERIC(10,7),
    destination_longitude   NUMERIC(10,7),
    distance_km             NUMERIC(10,2),

    -- Dates
    planned_start           TIMESTAMPTZ   NOT NULL,
    planned_arrival         TIMESTAMPTZ,
    actual_start            TIMESTAMPTZ,
    actual_end              TIMESTAMPTZ,

    -- Chargement (saisie globale, decision D14)
    cargo_description       VARCHAR(255),
    cargo_weight_kg         NUMERIC(10,2),
    cargo_volume_m3         NUMERIC(10,2),

    -- Financier
    revenue_amount          NUMERIC(15,2) NOT NULL DEFAULT 0,
    tariff_id               BIGINT,
    revenue_note            VARCHAR(255),
    fuel_cost               NUMERIC(15,2) NOT NULL DEFAULT 0,
    toll_cost               NUMERIC(15,2) NOT NULL DEFAULT 0,
    driver_cost             NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_cost              NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_cost              NUMERIC(15,2) GENERATED ALWAYS AS
                            (fuel_cost + toll_cost + driver_cost + other_cost) STORED,
    margin_amount           NUMERIC(15,2) GENERATED ALWAYS AS
                            (revenue_amount - fuel_cost - toll_cost - driver_cost - other_cost) STORED,

    -- Suivi
    progress                NUMERIC(5,2)  NOT NULL DEFAULT 0,
    status                  VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    cancellation_reason     VARCHAR(40),
    cancellation_comment    VARCHAR(500),

    external_reference      VARCHAR(60),
    created_by_user_id      BIGINT,

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT uk_missions_number UNIQUE (mission_number),
    CONSTRAINT fk_missions_client       FOREIGN KEY (client_id)           REFERENCES clients (id),
    CONSTRAINT fk_missions_service_type FOREIGN KEY (service_type_id)     REFERENCES service_types (id),
    CONSTRAINT fk_missions_vehicle      FOREIGN KEY (vehicle_id)          REFERENCES vehicles (id),
    CONSTRAINT fk_missions_driver       FOREIGN KEY (driver_id)           REFERENCES drivers (id),
    CONSTRAINT fk_missions_agency       FOREIGN KEY (agency_id)           REFERENCES agencies (id),
    CONSTRAINT fk_missions_route        FOREIGN KEY (route_id)            REFERENCES routes (id),
    CONSTRAINT fk_missions_origin       FOREIGN KEY (origin_city_id)      REFERENCES cities (id),
    CONSTRAINT fk_missions_destination  FOREIGN KEY (destination_city_id) REFERENCES cities (id),
    CONSTRAINT fk_missions_tariff       FOREIGN KEY (tariff_id)           REFERENCES tariffs (id),
    CONSTRAINT fk_missions_user         FOREIGN KEY (created_by_user_id)  REFERENCES users (id),

    CONSTRAINT ck_missions_status CHECK (status IN ('EN_ATTENTE', 'EN_COURS', 'TERMINEE', 'ANNULEE')),
    CONSTRAINT ck_missions_cancellation CHECK (cancellation_reason IS NULL OR cancellation_reason IN
        ('PANNE', 'ANNULATION_CLIENT', 'INDISPONIBILITE_CHAUFFEUR', 'METEO', 'AUTRE')),
    CONSTRAINT ck_missions_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_missions_revenue  CHECK (revenue_amount >= 0),
    CONSTRAINT ck_missions_dates    CHECK (actual_end IS NULL OR actual_start IS NULL OR actual_end >= actual_start),
    -- Une mission annulee porte toujours un motif (RG-5.7)
    CONSTRAINT ck_missions_cancel_requires_reason CHECK (
        status <> 'ANNULEE' OR cancellation_reason IS NOT NULL)
);

CREATE INDEX ix_missions_status        ON missions (status, planned_start);
CREATE INDEX ix_missions_vehicle       ON missions (vehicle_id, planned_start DESC);
CREATE INDEX ix_missions_driver        ON missions (driver_id, planned_start DESC);
CREATE INDEX ix_missions_client        ON missions (client_id, actual_end);
CREATE INDEX ix_missions_route         ON missions (route_id, actual_end);
CREATE INDEX ix_missions_agency        ON missions (agency_id, actual_end);
CREATE INDEX ix_missions_planned_start ON missions (planned_start DESC);


-- ---------------------------------------------------------------------
-- ETAPES INTERMEDIAIRES
-- Tournee urbaine desservant plusieurs points de livraison.
-- ---------------------------------------------------------------------
CREATE TABLE mission_waypoints (
    id                  BIGSERIAL PRIMARY KEY,
    mission_id          BIGINT        NOT NULL,
    sequence_number     INTEGER       NOT NULL,
    label               VARCHAR(150)  NOT NULL,
    address             VARCHAR(255),
    latitude            NUMERIC(10,7),
    longitude           NUMERIC(10,7),
    planned_arrival     TIMESTAMPTZ,
    actual_arrival      TIMESTAMPTZ,
    status              VARCHAR(20)   NOT NULL DEFAULT 'EN_ATTENTE',
    notes               VARCHAR(255),

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_waypoints_sequence UNIQUE (mission_id, sequence_number),
    CONSTRAINT fk_waypoints_mission FOREIGN KEY (mission_id) REFERENCES missions (id) ON DELETE CASCADE,
    CONSTRAINT ck_waypoints_status CHECK (status IN ('EN_ATTENTE', 'ATTEINT', 'IGNORE'))
);

CREATE INDEX ix_waypoints_mission ON mission_waypoints (mission_id, sequence_number);


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('mission.revenue_reminder_hours', '48',   'INTEGER', 'MISSION', 'Delai avant alerte de CA non saisi (heures)',      'system'),
    ('mission.number_prefix',          'MS',   'STRING',  'MISSION', 'Prefixe des numeros de mission',                    'system'),
    ('mission.punctuality_margin_min', '60',   'INTEGER', 'MISSION', 'Tolerance de ponctualite (minutes)',                'system'),
    ('mission.driver_cost_auto',       'true', 'BOOLEAN', 'MISSION', 'Imputer automatiquement la quote-part chauffeur',   'system');
