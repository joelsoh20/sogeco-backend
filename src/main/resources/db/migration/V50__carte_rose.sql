-- ---------------------------------------------------------------------
-- Carte rose CEMAC (attestation d'assurance automobile) : desormais
-- exigee pour chaque camion, au meme titre que l'assurance et la
-- visite technique. Champs administratifs recopies tels qu'imprimes
-- sur le document physique -- pas une reference dynamique a Vehicle
-- (qui peut evoluer independamment), meme logique que cartes_grises
-- (V21). La societe d'assurance reutilise le referentiel Partner deja
-- en place pour les polices d'assurance (InsurancePolicy.insurer),
-- plutot qu'un champ texte libre duplique.
-- ---------------------------------------------------------------------

CREATE TABLE cartes_roses (
    id                          BIGSERIAL PRIMARY KEY,
    vehicle_id                  BIGINT        NOT NULL,
    insurer_id                  BIGINT        NOT NULL,
    insured_name                VARCHAR(150)  NOT NULL,
    insured_address             VARCHAR(255),
    issuing_bureau_name         VARCHAR(150)  NOT NULL,
    issuing_bureau_address      VARCHAR(255),
    registration_number         VARCHAR(20)   NOT NULL,
    vehicle_make_type           VARCHAR(100)  NOT NULL,
    vehicle_category            VARCHAR(60)   NOT NULL,
    valid_from                  DATE          NOT NULL,
    valid_to                    DATE          NOT NULL,
    cost                        NUMERIC(15,2),
    notes                       VARCHAR(500),
    created_by_user_id          BIGINT,

    version                     INTEGER       NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    created_by                  VARCHAR(150),
    updated_by                  VARCHAR(150),

    CONSTRAINT fk_cartes_roses_vehicle    FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_cartes_roses_insurer    FOREIGN KEY (insurer_id) REFERENCES partners (id),
    CONSTRAINT fk_cartes_roses_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_cartes_roses_dates      CHECK (valid_to > valid_from)
);

CREATE INDEX ix_cartes_roses_vehicle ON cartes_roses (vehicle_id, valid_to DESC);
CREATE INDEX ix_cartes_roses_expiry  ON cartes_roses (valid_to);
CREATE INDEX ix_cartes_roses_created_by ON cartes_roses (created_by_user_id);

INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('compliance.carte_rose_validity_years', '1', 'INTEGER', 'CONFORMITE',
     'Duree de validite par defaut d''une carte rose (annees)', 'system');
