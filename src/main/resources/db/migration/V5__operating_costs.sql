-- =====================================================================
-- V5 — Couts d'exploitation
--
-- Tables : partners, fuel_logs, maintenance_logs, maintenance_items,
--          expenses
--
-- Ces tables remplacent les champs texte libres garageName et
-- stationName de l'analyse initiale : sans entite Partenaire, les
-- analyses "par prestataire" demandees aux modules 6 et 7 seraient
-- impossibles.
-- =====================================================================


-- ---------------------------------------------------------------------
-- PARTENAIRES
-- Garages, stations-service, assureurs, centres de visite technique.
-- Table unique avec discriminant : ils partagent les memes attributs.
-- ---------------------------------------------------------------------
CREATE TABLE partners (
    id                  BIGSERIAL PRIMARY KEY,
    code                VARCHAR(20),
    name                VARCHAR(150)  NOT NULL,
    partner_type        VARCHAR(30)   NOT NULL,
    contact_name        VARCHAR(120),
    phone               VARCHAR(30),
    email               VARCHAR(150),
    address             VARCHAR(255),
    city_id             BIGINT,
    tax_number          VARCHAR(50),
    notes               VARCHAR(500),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_partners_code UNIQUE (code),
    CONSTRAINT fk_partners_city FOREIGN KEY (city_id) REFERENCES cities (id),
    CONSTRAINT ck_partners_type CHECK (partner_type IN
        ('GARAGE', 'STATION_SERVICE', 'ASSUREUR', 'CENTRE_VISITE', 'FOURNISSEUR', 'AUTRE'))
);

CREATE INDEX ix_partners_type   ON partners (partner_type, active);
CREATE INDEX ix_partners_active ON partners (active);


-- ---------------------------------------------------------------------
-- RAVITAILLEMENTS
--
-- odometer_before et odometer_after reprennent les colonnes "Km avant"
-- et "Km apres" de la maquette Carburant : deux valeurs valent mieux
-- qu'une pour fiabiliser le calcul de consommation.
--
-- full_tank conditionne le calcul : seuls deux pleins complets
-- successifs donnent une consommation exploitable (RG-6.2).
-- ---------------------------------------------------------------------
CREATE TABLE fuel_logs (
    id                      BIGSERIAL PRIMARY KEY,
    vehicle_id              BIGINT        NOT NULL,
    driver_id               BIGINT,
    mission_id              BIGINT,
    partner_id              BIGINT,

    fuel_datetime           TIMESTAMPTZ   NOT NULL,
    quantity_liters         NUMERIC(8,2)  NOT NULL,
    unit_price              NUMERIC(10,2) NOT NULL,
    total_cost              NUMERIC(15,2) NOT NULL,

    odometer_before         NUMERIC(12,2),
    odometer_after          NUMERIC(12,2) NOT NULL,
    full_tank               BOOLEAN       NOT NULL DEFAULT TRUE,

    receipt_number          VARCHAR(50),
    receipt_document_id     BIGINT,

    -- Consommation calculee entre deux pleins complets, en L/100 km
    computed_consumption    NUMERIC(6,2),
    status                  VARCHAR(20)   NOT NULL DEFAULT 'VALIDE',
    anomaly_reason          VARCHAR(255),
    cancellation_reason     VARCHAR(255),

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_fuel_vehicle  FOREIGN KEY (vehicle_id)          REFERENCES vehicles (id),
    CONSTRAINT fk_fuel_driver   FOREIGN KEY (driver_id)           REFERENCES drivers (id),
    CONSTRAINT fk_fuel_mission  FOREIGN KEY (mission_id)          REFERENCES missions (id),
    CONSTRAINT fk_fuel_partner  FOREIGN KEY (partner_id)          REFERENCES partners (id),
    CONSTRAINT fk_fuel_receipt  FOREIGN KEY (receipt_document_id) REFERENCES documents (id),

    CONSTRAINT ck_fuel_status CHECK (status IN ('VALIDE', 'ANOMALIE', 'ANNULE')),
    CONSTRAINT ck_fuel_quantity CHECK (quantity_liters > 0),
    CONSTRAINT ck_fuel_price    CHECK (unit_price >= 0),
    -- Le kilometrage ne peut que croitre (RG-6.4)
    CONSTRAINT ck_fuel_odometer CHECK (odometer_before IS NULL OR odometer_after >= odometer_before)
);

CREATE INDEX ix_fuel_vehicle_date ON fuel_logs (vehicle_id, fuel_datetime DESC);
CREATE INDEX ix_fuel_mission      ON fuel_logs (mission_id);
CREATE INDEX ix_fuel_driver       ON fuel_logs (driver_id, fuel_datetime DESC);
CREATE INDEX ix_fuel_status       ON fuel_logs (status);
CREATE INDEX ix_fuel_partner      ON fuel_logs (partner_id);


-- ---------------------------------------------------------------------
-- INTERVENTIONS DE MAINTENANCE
--
-- is_breakdown distingue une panne subie d'un entretien planifie :
-- c'est la base du ratio preventif / curatif, indicateur de maturite
-- du module (RG-7.3).
-- ---------------------------------------------------------------------
CREATE TABLE maintenance_logs (
    id                      BIGSERIAL PRIMARY KEY,
    vehicle_id              BIGINT        NOT NULL,
    partner_id              BIGINT,

    category                VARCHAR(30)   NOT NULL,
    description             VARCHAR(500)  NOT NULL,
    intervention_date       DATE          NOT NULL,
    completion_date         DATE,
    odometer_km             NUMERIC(12,2),

    parts_cost              NUMERIC(15,2) NOT NULL DEFAULT 0,
    labor_cost              NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_cost              NUMERIC(15,2) GENERATED ALWAYS AS (parts_cost + labor_cost) STORED,

    status                  VARCHAR(20)   NOT NULL DEFAULT 'PLANIFIEE',
    downtime_days           INTEGER       NOT NULL DEFAULT 0,
    is_breakdown            BOOLEAN       NOT NULL DEFAULT FALSE,
    is_recurrence           BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Code defaut moteur remonte par le boitier (sprint 5)
    error_code              VARCHAR(30),

    next_intervention_date  DATE,
    next_intervention_km    NUMERIC(12,2),
    invoice_document_id     BIGINT,

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY (vehicle_id)          REFERENCES vehicles (id),
    CONSTRAINT fk_maintenance_partner FOREIGN KEY (partner_id)          REFERENCES partners (id),
    CONSTRAINT fk_maintenance_invoice FOREIGN KEY (invoice_document_id) REFERENCES documents (id),

    CONSTRAINT ck_maintenance_category CHECK (category IN
        ('ENTRETIEN_PREVENTIF', 'REPARATION_MECANIQUE', 'PNEUMATIQUE', 'ELECTRICITE', 'AUTRES')),
    CONSTRAINT ck_maintenance_status CHECK (status IN
        ('PLANIFIEE', 'EN_COURS', 'TERMINEE', 'ANNULEE')),
    CONSTRAINT ck_maintenance_costs CHECK (parts_cost >= 0 AND labor_cost >= 0),
    CONSTRAINT ck_maintenance_dates CHECK (completion_date IS NULL OR completion_date >= intervention_date)
);

CREATE INDEX ix_maintenance_vehicle ON maintenance_logs (vehicle_id, intervention_date DESC);
CREATE INDEX ix_maintenance_status  ON maintenance_logs (status, intervention_date);
CREATE INDEX ix_maintenance_partner ON maintenance_logs (partner_id);
CREATE INDEX ix_maintenance_next    ON maintenance_logs (next_intervention_date)
    WHERE next_intervention_date IS NOT NULL;


-- ---------------------------------------------------------------------
-- LIGNES DE DETAIL
-- Le bloc "Pieces / Prestations" de la maquette Maintenance : le
-- fonctionnel exige une liste precise des operations realisees, avec
-- couts pieces et main-d'oeuvre separes (RG-7.2).
-- ---------------------------------------------------------------------
CREATE TABLE maintenance_items (
    id                      BIGSERIAL PRIMARY KEY,
    maintenance_log_id      BIGINT        NOT NULL,
    item_type               VARCHAR(20)   NOT NULL,
    label                   VARCHAR(150)  NOT NULL,
    quantity                NUMERIC(10,2) NOT NULL DEFAULT 1,
    unit_price              NUMERIC(15,2) NOT NULL,
    total                   NUMERIC(15,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_items_maintenance FOREIGN KEY (maintenance_log_id)
        REFERENCES maintenance_logs (id) ON DELETE CASCADE,
    CONSTRAINT ck_items_type CHECK (item_type IN ('PIECE', 'MAIN_OEUVRE')),
    CONSTRAINT ck_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_items_price CHECK (unit_price >= 0)
);

CREATE INDEX ix_items_maintenance ON maintenance_items (maintenance_log_id);


-- ---------------------------------------------------------------------
-- DEPENSES DIVERSES
--
-- Sans cette table, la repartition "Carburant / Maintenance / Salaires
-- / Peages / Assurances / Autres" du tableau de bord de direction est
-- incalculable : les postes hors carburant et maintenance n'auraient
-- nulle part ou vivre.
-- ---------------------------------------------------------------------
CREATE TABLE expenses (
    id                      BIGSERIAL PRIMARY KEY,
    expense_date            DATE          NOT NULL,
    category                VARCHAR(30)   NOT NULL,
    label                   VARCHAR(200)  NOT NULL,
    amount                  NUMERIC(15,2) NOT NULL,

    vehicle_id              BIGINT,
    driver_id               BIGINT,
    mission_id              BIGINT,
    agency_id               BIGINT,
    partner_id              BIGINT,
    document_id             BIGINT,
    created_by_user_id      BIGINT,
    notes                   VARCHAR(500),

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_expenses_vehicle  FOREIGN KEY (vehicle_id)         REFERENCES vehicles (id),
    CONSTRAINT fk_expenses_driver   FOREIGN KEY (driver_id)          REFERENCES drivers (id),
    CONSTRAINT fk_expenses_mission  FOREIGN KEY (mission_id)         REFERENCES missions (id),
    CONSTRAINT fk_expenses_agency   FOREIGN KEY (agency_id)          REFERENCES agencies (id),
    CONSTRAINT fk_expenses_partner  FOREIGN KEY (partner_id)         REFERENCES partners (id),
    CONSTRAINT fk_expenses_document FOREIGN KEY (document_id)        REFERENCES documents (id),
    CONSTRAINT fk_expenses_user     FOREIGN KEY (created_by_user_id) REFERENCES users (id),

    CONSTRAINT ck_expenses_category CHECK (category IN
        ('SALAIRE', 'PEAGE', 'AMENDE', 'ADMINISTRATIF', 'ASSURANCE', 'MANUTENTION', 'AUTRE')),
    CONSTRAINT ck_expenses_amount CHECK (amount >= 0)
);

CREATE INDEX ix_expenses_date     ON expenses (expense_date DESC);
CREATE INDEX ix_expenses_category ON expenses (category, expense_date DESC);
CREATE INDEX ix_expenses_vehicle  ON expenses (vehicle_id, expense_date DESC);
CREATE INDEX ix_expenses_mission  ON expenses (mission_id);
CREATE INDEX ix_expenses_agency   ON expenses (agency_id, expense_date DESC);


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('fuel.consumption_history_size',  '5',    'INTEGER', 'CARBURANT',  'Nombre de pleins pour la moyenne mobile',            'system'),
    ('fuel.require_receipt_photo',     'true', 'BOOLEAN', 'CARBURANT',  'Exiger la photo du compteur a la saisie',            'system'),
    ('fuel.edit_window_hours',         '24',   'INTEGER', 'CARBURANT',  'Delai de modification d''un plein par le gestionnaire (heures)', 'system'),
    ('maintenance.recurrence_days',    '30',   'INTEGER', 'MAINTENANCE','Fenetre de detection des retours en atelier (jours)', 'system'),
    ('maintenance.renewal_cost_ratio', '40',   'INTEGER', 'MAINTENANCE','Part de la valeur d''acquisition declenchant une alerte de renouvellement (%)', 'system');
