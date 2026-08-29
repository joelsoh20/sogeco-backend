-- =====================================================================
-- V15 — Carte bleue et licence de transport
--
-- Tables : cartes_bleues, transport_licenses
--
-- Meme principe que V7 (assurances, visites techniques) : profondeur
-- metier au-dessus du document generique existant, avec un lien
-- facultatif vers celui-ci pour la piece scannee.
--
-- Difference de portee entre les deux :
--   - carte bleue : par camion, comme une visite technique
--     (vehicle_id obligatoire).
--   - licence de transport : pour la flotte entiere, jamais liee a
--     un camion precis (pas de colonne vehicle_id du tout).
-- =====================================================================


-- ---------------------------------------------------------------------
-- CARTE BLEUE (par camion)
-- ---------------------------------------------------------------------
CREATE TABLE cartes_bleues (
    id                  BIGSERIAL PRIMARY KEY,
    vehicle_id          BIGINT        NOT NULL,
    card_number         VARCHAR(50)   NOT NULL,
    issuing_authority   VARCHAR(150),
    issue_date          DATE          NOT NULL,
    expiry_date         DATE          NOT NULL,
    cost                NUMERIC(15,2),
    notes               VARCHAR(500),
    document_id         BIGINT,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_cartes_bleues_number UNIQUE (card_number),
    CONSTRAINT fk_cartes_bleues_vehicle  FOREIGN KEY (vehicle_id)  REFERENCES vehicles (id),
    CONSTRAINT fk_cartes_bleues_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_cartes_bleues_dates CHECK (expiry_date > issue_date)
);

CREATE INDEX ix_cartes_bleues_vehicle ON cartes_bleues (vehicle_id, expiry_date DESC);
CREATE INDEX ix_cartes_bleues_expiry  ON cartes_bleues (expiry_date);


-- ---------------------------------------------------------------------
-- LICENCE DE TRANSPORT (flotte entiere)
--
-- Un seul document couvre toute la flotte — pas de vehicle_id. Le
-- statut suit le meme cycle de vie qu'une police d'assurance
-- (ACTIVE / EXPIREE / RESILIEE), reutilise a l'identique.
-- ---------------------------------------------------------------------
CREATE TABLE transport_licenses (
    id                  BIGSERIAL PRIMARY KEY,
    license_number      VARCHAR(50)   NOT NULL,
    issuing_authority   VARCHAR(150),
    issue_date          DATE          NOT NULL,
    expiry_date         DATE          NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    cost                NUMERIC(15,2),
    notes               VARCHAR(500),
    document_id         BIGINT,

    version             INTEGER       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(150),
    updated_by          VARCHAR(150),

    CONSTRAINT uk_transport_licenses_number UNIQUE (license_number),
    CONSTRAINT fk_transport_licenses_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_transport_licenses_status CHECK (status IN ('ACTIVE', 'EXPIREE', 'RESILIEE')),
    CONSTRAINT ck_transport_licenses_dates CHECK (expiry_date > issue_date)
);

CREATE INDEX ix_transport_licenses_end ON transport_licenses (expiry_date) WHERE status = 'ACTIVE';


-- ---------------------------------------------------------------------
-- Type de document generique : ajout de CARTE_BLEUE (LICENCE_TRANSPORT
-- existe deja) pour permettre le rattachement d'un scan sous le bon
-- libelle depuis le flux d'upload generique.
-- ---------------------------------------------------------------------
ALTER TABLE documents DROP CONSTRAINT ck_documents_type;
ALTER TABLE documents ADD CONSTRAINT ck_documents_type CHECK (document_type IN
    ('CARTE_GRISE', 'CARTE_BLEUE', 'CHRONOTACHYGRAPHE', 'LICENCE_TRANSPORT', 'AUTORISATION_CIRCULER',
     'ASSURANCE', 'VISITE_TECHNIQUE', 'PERMIS_CONDUIRE', 'CONTRAT_TRAVAIL',
     'PHOTO', 'BON_LIVRAISON', 'ORDRE_MISSION', 'FACTURE', 'RECU_CARBURANT', 'AUTRE'));
