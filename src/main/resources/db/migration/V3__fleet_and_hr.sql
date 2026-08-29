-- =====================================================================
-- V3 — Parc et ressources humaines
--
-- Tables : documents, vehicles, drivers, vehicle_assignments,
--          driver_ratings, driver_bonuses, driver_actions
--
-- Ordre de creation impose par les cles etrangeres : documents en
-- premier, car vehicles et drivers referencent leur photo.
-- =====================================================================


-- ---------------------------------------------------------------------
-- DOCUMENTS
--
-- Table polymorphe : entity_type + entity_id designent le porteur
-- (VEHICLE, DRIVER, MISSION, MAINTENANCE, INSURANCE...). Pas de cle
-- etrangere sur entity_id, c'est le prix de la generisation.
--
-- expiry_date alimente l'echeancier unifie du bloc "Documents &
-- Echeances" de la fiche camion.
-- ---------------------------------------------------------------------
CREATE TABLE documents (
    id                  BIGSERIAL PRIMARY KEY,
    entity_type         VARCHAR(40)   NOT NULL,
    entity_id           BIGINT,
    document_type       VARCHAR(40)   NOT NULL,
    file_name           VARCHAR(255)  NOT NULL,
    file_path           VARCHAR(500)  NOT NULL,
    mime_type           VARCHAR(100),
    file_size           BIGINT,
    reference_number    VARCHAR(60),
    issue_date          DATE,
    expiry_date         DATE,
    status              VARCHAR(20)   NOT NULL DEFAULT 'VALIDE',
    notes              VARCHAR(255),
    uploaded_by_user_id BIGINT,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT fk_documents_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_documents_entity_type CHECK (entity_type IN
        ('VEHICLE', 'DRIVER', 'MISSION', 'MAINTENANCE', 'INSURANCE', 'INSPECTION', 'CLAIM', 'FUEL', 'OTHER')),
    CONSTRAINT ck_documents_type CHECK (document_type IN
        ('CARTE_GRISE', 'CHRONOTACHYGRAPHE', 'LICENCE_TRANSPORT', 'AUTORISATION_CIRCULER',
         'ASSURANCE', 'VISITE_TECHNIQUE', 'PERMIS_CONDUIRE', 'CONTRAT_TRAVAIL',
         'PHOTO', 'BON_LIVRAISON', 'ORDRE_MISSION', 'FACTURE', 'RECU_CARBURANT', 'AUTRE')),
    CONSTRAINT ck_documents_status CHECK (status IN ('VALIDE', 'A_RENOUVELER', 'EXPIRE', 'SANS_ECHEANCE'))
);

CREATE INDEX ix_documents_entity ON documents (entity_type, entity_id);
CREATE INDEX ix_documents_expiry ON documents (expiry_date) WHERE expiry_date IS NOT NULL;
CREATE INDEX ix_documents_status ON documents (status);


-- ---------------------------------------------------------------------
-- CAMIONS
-- ---------------------------------------------------------------------
CREATE TABLE vehicles (
    id                      BIGSERIAL PRIMARY KEY,

    -- Identite administrative
    registration_number     VARCHAR(20)   NOT NULL,
    vin_number              VARCHAR(30),
    brand                   VARCHAR(60)   NOT NULL,
    model                   VARCHAR(60)   NOT NULL,
    body_type               VARCHAR(20)   NOT NULL,
    capacity_tons           NUMERIC(6,2),
    tank_capacity_liters    NUMERIC(8,2),
    gross_weight_kg         NUMERIC(10,2),
    first_registration_date DATE,
    owner_name              VARCHAR(120),

    -- Acquisition
    purchase_date           DATE,
    purchase_price          NUMERIC(15,2),

    -- Exploitation
    status                  VARCHAR(20)   NOT NULL DEFAULT 'DISPONIBLE',
    agency_id               BIGINT,
    device_id               VARCHAR(60),

    -- Compteurs (denormalises, recalcules)
    current_kilometers      NUMERIC(12,2) NOT NULL DEFAULT 0,
    daily_km                NUMERIC(10,2) NOT NULL DEFAULT 0,
    fuel_level_percent      NUMERIC(5,2),
    fuel_level_liters       NUMERIC(8,2),
    avg_fuel_consumption    NUMERIC(6,2),

    -- Maintenance preventive
    next_maintenance_date   DATE,
    next_maintenance_km     NUMERIC(12,2),

    photo_document_id       BIGINT,
    active                  BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at              TIMESTAMPTZ,

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT uk_vehicles_registration UNIQUE (registration_number),
    CONSTRAINT uk_vehicles_vin          UNIQUE (vin_number),
    CONSTRAINT uk_vehicles_device       UNIQUE (device_id),
    CONSTRAINT fk_vehicles_agency FOREIGN KEY (agency_id) REFERENCES agencies (id),
    CONSTRAINT fk_vehicles_photo  FOREIGN KEY (photo_document_id) REFERENCES documents (id),
    CONSTRAINT ck_vehicles_status CHECK (status IN
        ('DISPONIBLE', 'EN_MISSION', 'EN_MAINTENANCE', 'EN_PANNE', 'HORS_SERVICE')),
    CONSTRAINT ck_vehicles_body_type CHECK (body_type IN
        ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU')),
    -- Le kilometrage ne peut jamais etre negatif (RG-4.6)
    CONSTRAINT ck_vehicles_kilometers CHECK (current_kilometers >= 0),
    CONSTRAINT ck_vehicles_fuel_percent CHECK (fuel_level_percent IS NULL
        OR (fuel_level_percent BETWEEN 0 AND 100))
);

CREATE INDEX ix_vehicles_agency ON vehicles (agency_id);
CREATE INDEX ix_vehicles_status ON vehicles (status);
CREATE INDEX ix_vehicles_active ON vehicles (active);


-- ---------------------------------------------------------------------
-- CHAUFFEURS
--
-- user_id est optionnel : la decision D3 ouvre un compte en consultation
-- aux chauffeurs, mais il n'est pas obligatoire.
-- ---------------------------------------------------------------------
CREATE TABLE drivers (
    id                  BIGSERIAL PRIMARY KEY,
    matricule           VARCHAR(30)   NOT NULL,
    first_name          VARCHAR(80)   NOT NULL,
    last_name           VARCHAR(80)   NOT NULL,
    phone               VARCHAR(30),
    birth_date          DATE,
    hire_date           DATE          NOT NULL,
    job_title           VARCHAR(80),

    -- Permis de conduire
    license_number      VARCHAR(50),
    license_category    VARCHAR(20),
    license_expiry_date DATE,

    monthly_salary      NUMERIC(15,2),
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIF',
    agency_id           BIGINT,
    user_id             BIGINT,

    -- Compteurs denormalises, recalcules mensuellement
    performance_score   NUMERIC(5,2),
    incidents_count     INTEGER       NOT NULL DEFAULT 0,
    total_missions      INTEGER       NOT NULL DEFAULT 0,
    total_kilometers    NUMERIC(12,2) NOT NULL DEFAULT 0,

    photo_document_id   BIGINT,
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at          TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_drivers_matricule UNIQUE (matricule),
    CONSTRAINT uk_drivers_user      UNIQUE (user_id),
    CONSTRAINT fk_drivers_agency FOREIGN KEY (agency_id) REFERENCES agencies (id),
    CONSTRAINT fk_drivers_user   FOREIGN KEY (user_id)   REFERENCES users (id),
    CONSTRAINT fk_drivers_photo  FOREIGN KEY (photo_document_id) REFERENCES documents (id),
    CONSTRAINT ck_drivers_status CHECK (status IN ('ACTIF', 'EN_CONGE', 'SUSPENDU', 'SORTI')),
    CONSTRAINT ck_drivers_score CHECK (performance_score IS NULL
        OR (performance_score BETWEEN 0 AND 100))
);

CREATE INDEX ix_drivers_agency ON drivers (agency_id);
CREATE INDEX ix_drivers_status ON drivers (status);
CREATE INDEX ix_drivers_license_expiry ON drivers (license_expiry_date)
    WHERE license_expiry_date IS NOT NULL;


-- ---------------------------------------------------------------------
-- AFFECTATIONS
--
-- Remplace la relation directe camion <-> chauffeur. Historisee : un
-- chauffeur change de camion sans que l'historique soit perdu.
-- ---------------------------------------------------------------------
CREATE TABLE vehicle_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT        NOT NULL,
    driver_id           BIGINT        NOT NULL,
    start_date          DATE          NOT NULL,
    end_date            DATE,
    assigned_by_user_id BIGINT,
    notes               VARCHAR(255),

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT fk_assignments_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_assignments_driver  FOREIGN KEY (driver_id)  REFERENCES drivers (id),
    CONSTRAINT fk_assignments_user    FOREIGN KEY (assigned_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_assignments_dates CHECK (end_date IS NULL OR end_date >= start_date)
);

-- Un seul chauffeur affecte a la fois par camion (RG-9.4).
-- L'index unique partiel fait respecter la regle par la base, et non
-- par le code applicatif : deux requetes concurrentes ne peuvent pas
-- creer deux affectations actives.
CREATE UNIQUE INDEX ux_assignments_active_vehicle
    ON vehicle_assignments (vehicle_id) WHERE end_date IS NULL;

-- Un chauffeur ne conduit qu'un camion a la fois.
CREATE UNIQUE INDEX ux_assignments_active_driver
    ON vehicle_assignments (driver_id) WHERE end_date IS NULL;

CREATE INDEX ix_assignments_vehicle ON vehicle_assignments (vehicle_id, start_date DESC);
CREATE INDEX ix_assignments_driver  ON vehicle_assignments (driver_id, start_date DESC);


-- ---------------------------------------------------------------------
-- NOTATION DES CHAUFFEURS
--
-- Une ligne par critere et par mois. Les quatre premiers criteres sont
-- calcules, RESPECT_REGLES est saisi par un responsable (RG-9.6).
-- ---------------------------------------------------------------------
CREATE TABLE driver_ratings (
    id                  BIGSERIAL PRIMARY KEY,
    driver_id           BIGINT        NOT NULL,
    period_month        DATE          NOT NULL,
    criterion           VARCHAR(40)   NOT NULL,
    score_100           NUMERIC(5,2)  NOT NULL,
    is_automatic        BOOLEAN       NOT NULL DEFAULT TRUE,
    comment             VARCHAR(500),
    rated_by_user_id    BIGINT,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_ratings_driver_period_criterion UNIQUE (driver_id, period_month, criterion),
    CONSTRAINT fk_ratings_driver FOREIGN KEY (driver_id) REFERENCES drivers (id),
    CONSTRAINT fk_ratings_user   FOREIGN KEY (rated_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_ratings_criterion CHECK (criterion IN
        ('CONDUITE_SECURISEE', 'CONSOMMATION_ECONOMIQUE', 'RESPECT_DELAIS',
         'ENTRETIEN_VEHICULE', 'RESPECT_REGLES')),
    CONSTRAINT ck_ratings_score CHECK (score_100 BETWEEN 0 AND 100)
);

CREATE INDEX ix_ratings_driver_period ON driver_ratings (driver_id, period_month DESC);


-- ---------------------------------------------------------------------
-- PRIMES DE PERFORMANCE
--
-- Circuit : PROPOSEE (calcul automatique) -> VALIDEE -> VERSEE.
-- Une seule prime par chauffeur et par mois.
-- ---------------------------------------------------------------------
CREATE TABLE driver_bonuses (
    id                  BIGSERIAL PRIMARY KEY,
    driver_id           BIGINT        NOT NULL,
    period_month        DATE          NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    performance_score   NUMERIC(5,2),
    reason              VARCHAR(255),
    status              VARCHAR(20)   NOT NULL DEFAULT 'PROPOSEE',
    granted_by_user_id  BIGINT,
    granted_at          TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_bonuses_driver_period UNIQUE (driver_id, period_month),
    CONSTRAINT fk_bonuses_driver FOREIGN KEY (driver_id) REFERENCES drivers (id),
    CONSTRAINT fk_bonuses_user   FOREIGN KEY (granted_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_bonuses_status CHECK (status IN ('PROPOSEE', 'VALIDEE', 'VERSEE', 'REFUSEE')),
    CONSTRAINT ck_bonuses_amount CHECK (amount >= 0)
);

CREATE INDEX ix_bonuses_period ON driver_bonuses (period_month DESC, status);


-- ---------------------------------------------------------------------
-- ACTIONS RH
--
-- Trace les trois boutons de la fiche chauffeur : attribuer une prime,
-- envoyer un avertissement, prevoir une formation (RG-9.12).
-- ---------------------------------------------------------------------
CREATE TABLE driver_actions (
    id                  BIGSERIAL PRIMARY KEY,
    driver_id           BIGINT        NOT NULL,
    action_type         VARCHAR(30)   NOT NULL,
    action_date         DATE          NOT NULL,
    motif               VARCHAR(255)  NOT NULL,
    comment             VARCHAR(1000),
    document_id         BIGINT,
    created_by_user_id  BIGINT,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT fk_actions_driver   FOREIGN KEY (driver_id) REFERENCES drivers (id),
    CONSTRAINT fk_actions_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_actions_user     FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_actions_type CHECK (action_type IN
        ('PRIME', 'AVERTISSEMENT', 'FORMATION', 'ENTRETIEN', 'FELICITATION'))
);

CREATE INDEX ix_actions_driver ON driver_actions (driver_id, action_date DESC);


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('maintenance.preventive_km_warning',  '500',   'INTEGER', 'MAINTENANCE', 'Preavis de maintenance preventive (km)',        'system'),
    ('maintenance.vidange_interval_km',    '10000', 'INTEGER', 'MAINTENANCE', 'Intervalle de vidange par defaut (km)',          'system'),
    ('bonus.score_threshold_excellent',    '90',    'INTEGER', 'PERFORMANCE', 'Score minimal pour la prime maximale',           'system'),
    ('bonus.score_threshold_good',         '70',    'INTEGER', 'PERFORMANCE', 'Score minimal pour une prime',                   'system'),
    ('bonus.amount_excellent',             '450000','DECIMAL', 'PERFORMANCE', 'Montant de prime, palier excellent (FCFA)',      'system'),
    ('bonus.amount_good',                  '200000','DECIMAL', 'PERFORMANCE', 'Montant de prime, palier bon (FCFA)',            'system'),
    ('document.blocking_types',            'ASSURANCE,VISITE_TECHNIQUE,CARTE_GRISE', 'STRING', 'CONFORMITE',
        'Documents dont l''expiration bloque l''affectation', 'system');
