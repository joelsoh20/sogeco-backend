-- =====================================================================
-- V7 — Conformite : assurances, visites techniques, sinistres
--
-- Tables : insurance_policies, policy_vehicles, technical_inspections,
--          claims
--
-- Principe directeur : ces tables ajoutent la profondeur METIER
-- (assureur, prime, resultat de controle, sinistre) au-dessus du
-- document generique deja existant depuis le sprint 2. La creation
-- d'une police ou d'une visite met a jour le document ASSURANCE ou
-- VISITE_TECHNIQUE correspondant : le blocage d'affectation d'un
-- camion (RG-4.5, VehicleService.blockingReasons) continue de
-- fonctionner sans aucune modification.
-- =====================================================================


-- ---------------------------------------------------------------------
-- CONTRATS D'ASSURANCE
--
-- Une police peut couvrir plusieurs camions (contrat flotte, courant
-- au Cameroun) : la relation vehicule est portee par policy_vehicles,
-- pas par une colonne unique. Un seul camion se traite comme un cas
-- particulier a une seule ligne.
-- ---------------------------------------------------------------------
CREATE TABLE insurance_policies (
    id                  BIGSERIAL PRIMARY KEY,
    policy_number       VARCHAR(50)   NOT NULL,
    partner_id          BIGINT        NOT NULL,
    coverage_type       VARCHAR(30)   NOT NULL,
    premium_amount      NUMERIC(15,2),
    payment_frequency   VARCHAR(20)   NOT NULL DEFAULT 'ANNUEL',
    start_date          DATE          NOT NULL,
    end_date            DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    document_id         BIGINT,
    notes               VARCHAR(500),

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_policies_number UNIQUE (policy_number),
    CONSTRAINT fk_policies_partner  FOREIGN KEY (partner_id)  REFERENCES partners (id),
    CONSTRAINT fk_policies_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_policies_coverage CHECK (coverage_type IN
        ('TOUS_RISQUES', 'TIERS', 'TIERS_VOL_INCENDIE')),
    CONSTRAINT ck_policies_frequency CHECK (payment_frequency IN
        ('ANNUEL', 'SEMESTRIEL', 'TRIMESTRIEL', 'MENSUEL')),
    CONSTRAINT ck_policies_status CHECK (status IN ('ACTIVE', 'EXPIREE', 'RESILIEE')),
    CONSTRAINT ck_policies_dates CHECK (end_date > start_date)
);

CREATE INDEX ix_policies_partner ON insurance_policies (partner_id);
CREATE INDEX ix_policies_end     ON insurance_policies (end_date) WHERE status = 'ACTIVE';

CREATE TABLE policy_vehicles (
    insurance_policy_id BIGINT NOT NULL,
    vehicle_id           BIGINT NOT NULL,

    CONSTRAINT pk_policy_vehicles PRIMARY KEY (insurance_policy_id, vehicle_id),
    CONSTRAINT fk_pv_policy  FOREIGN KEY (insurance_policy_id) REFERENCES insurance_policies (id) ON DELETE CASCADE,
    CONSTRAINT fk_pv_vehicle FOREIGN KEY (vehicle_id)          REFERENCES vehicles (id)
);

CREATE INDEX ix_pv_vehicle ON policy_vehicles (vehicle_id);


-- ---------------------------------------------------------------------
-- VISITES TECHNIQUES
--
-- Le resultat (conforme, non conforme, avec reserves) est ce que le
-- document generique ne capture pas : lui ne connait qu'une date
-- d'expiration, pas l'issue du controle.
-- ---------------------------------------------------------------------
CREATE TABLE technical_inspections (
    id                      BIGSERIAL PRIMARY KEY,
    vehicle_id              BIGINT        NOT NULL,
    partner_id              BIGINT,
    inspection_date         DATE          NOT NULL,
    next_inspection_date    DATE          NOT NULL,
    result                  VARCHAR(30)   NOT NULL,
    defects_noted           VARCHAR(500),
    cost                    NUMERIC(15,2) NOT NULL DEFAULT 0,
    document_id             BIGINT,

    version                 INTEGER       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(150),
    updated_by              VARCHAR(150),

    CONSTRAINT fk_inspections_vehicle  FOREIGN KEY (vehicle_id)  REFERENCES vehicles (id),
    CONSTRAINT fk_inspections_partner  FOREIGN KEY (partner_id)  REFERENCES partners (id),
    CONSTRAINT fk_inspections_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_inspections_result CHECK (result IN
        ('CONFORME', 'NON_CONFORME', 'CONFORME_AVEC_RESERVES')),
    CONSTRAINT ck_inspections_dates CHECK (next_inspection_date > inspection_date),
    CONSTRAINT ck_inspections_cost CHECK (cost >= 0)
);

CREATE INDEX ix_inspections_vehicle ON technical_inspections (vehicle_id, inspection_date DESC);
CREATE INDEX ix_inspections_next    ON technical_inspections (next_inspection_date);


-- ---------------------------------------------------------------------
-- SINISTRES
--
-- Rattachement facultatif a une police (un accrochage mineur peut ne
-- jamais etre declare a l'assureur) et a une intervention de
-- maintenance (la reparation consecutive au sinistre).
-- ---------------------------------------------------------------------
CREATE TABLE claims (
    id                      BIGSERIAL PRIMARY KEY,
    claim_number             VARCHAR(50)   NOT NULL,
    vehicle_id                BIGINT        NOT NULL,
    driver_id                 BIGINT,
    insurance_policy_id       BIGINT,
    maintenance_log_id        BIGINT,

    incident_date              DATE          NOT NULL,
    claim_type                 VARCHAR(30)   NOT NULL,
    description                 VARCHAR(1000) NOT NULL,
    location_label              VARCHAR(255),
    police_report_number        VARCHAR(60),

    estimated_cost               NUMERIC(15,2),
    deductible_amount            NUMERIC(15,2),
    reimbursed_amount            NUMERIC(15,2),

    status                        VARCHAR(20)   NOT NULL DEFAULT 'DECLARE',
    document_id                  BIGINT,
    created_by_user_id            BIGINT,

    version                      INTEGER       NOT NULL DEFAULT 0,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ,
    created_by                   VARCHAR(150),
    updated_by                   VARCHAR(150),

    CONSTRAINT uk_claims_number UNIQUE (claim_number),
    CONSTRAINT fk_claims_vehicle     FOREIGN KEY (vehicle_id)          REFERENCES vehicles (id),
    CONSTRAINT fk_claims_driver      FOREIGN KEY (driver_id)           REFERENCES drivers (id),
    CONSTRAINT fk_claims_policy      FOREIGN KEY (insurance_policy_id) REFERENCES insurance_policies (id),
    CONSTRAINT fk_claims_maintenance FOREIGN KEY (maintenance_log_id)  REFERENCES maintenance_logs (id),
    CONSTRAINT fk_claims_document    FOREIGN KEY (document_id)         REFERENCES documents (id),
    CONSTRAINT fk_claims_user        FOREIGN KEY (created_by_user_id)  REFERENCES users (id),
    CONSTRAINT ck_claims_type CHECK (claim_type IN
        ('COLLISION', 'VOL', 'INCENDIE', 'DEGATS_MATERIELS', 'DOMMAGES_CORPORELS', 'AUTRE')),
    CONSTRAINT ck_claims_status CHECK (status IN
        ('DECLARE', 'EN_INSTRUCTION', 'ACCEPTE', 'REFUSE', 'CLOTURE'))
);

CREATE INDEX ix_claims_vehicle ON claims (vehicle_id, incident_date DESC);
CREATE INDEX ix_claims_status  ON claims (status, incident_date DESC);
CREATE INDEX ix_claims_policy  ON claims (insurance_policy_id);


-- ---------------------------------------------------------------------
-- PARAMETRES COMPLEMENTAIRES
-- ---------------------------------------------------------------------
INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('compliance.inspection_interval_months', '12',  'INTEGER', 'CONFORMITE', 'Intervalle par defaut entre deux visites techniques (mois)', 'system'),
    ('compliance.deadline_warning_days',      '60',  'INTEGER', 'CONFORMITE', 'Premier seuil d''alerte avant echeance (jours)',              'system');
