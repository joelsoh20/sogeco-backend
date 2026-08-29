-- =====================================================================
-- V21 — Carte grise (par camion)
--
-- Meme principe que V15 (carte bleue) : profondeur metier au-dessus du
-- document generique existant (CARTE_GRISE existe deja dans DocumentType
-- depuis le sprint 2), avec un lien facultatif vers celui-ci pour le scan.
--
-- Les champs marque/chassis/carrosserie/genre/date de mise en circulation
-- dupliquent volontairement certains attributs deja portes par Vehicle :
-- ce sont ceux imprimes sur le document physique au moment de sa
-- delivrance, pas une reference dynamique au camion (qui peut evoluer,
-- ex. correction d'une coquille sur la marque saisie a la creation).
--
-- expiry_date n'est pas systematiquement saisie au formulaire : calculee
-- par defaut a issue_date + compliance.carte_grise_validity_years (10 ans),
-- parametre au meme titre que l'intervalle de visite technique.
-- =====================================================================

CREATE TABLE cartes_grises (
    id                       BIGSERIAL PRIMARY KEY,
    vehicle_id               BIGINT        NOT NULL,
    registration_number      VARCHAR(20)   NOT NULL,
    chassis_number           VARCHAR(30)   NOT NULL,
    brand                    VARCHAR(60)   NOT NULL,
    genre                    VARCHAR(30),
    body_type                VARCHAR(20),
    seat_count               INTEGER,
    first_circulation_date   DATE,
    issue_date               DATE          NOT NULL,
    expiry_date              DATE          NOT NULL,
    cost                     NUMERIC(15,2),
    notes                    VARCHAR(500),
    document_id              BIGINT,

    version                  INTEGER       NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ,
    created_by               VARCHAR(150),
    updated_by               VARCHAR(150),

    CONSTRAINT fk_cartes_grises_vehicle  FOREIGN KEY (vehicle_id)  REFERENCES vehicles (id),
    CONSTRAINT fk_cartes_grises_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT ck_cartes_grises_body_type CHECK (body_type IS NULL OR body_type IN
        ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU', 'UTILITAIRE')),
    CONSTRAINT ck_cartes_grises_seats CHECK (seat_count IS NULL OR seat_count > 0),
    CONSTRAINT ck_cartes_grises_dates CHECK (expiry_date > issue_date)
);

CREATE INDEX ix_cartes_grises_vehicle ON cartes_grises (vehicle_id, expiry_date DESC);
CREATE INDEX ix_cartes_grises_expiry  ON cartes_grises (expiry_date);

INSERT INTO system_settings (setting_key, setting_value, value_type, category, label, created_by) VALUES
    ('compliance.carte_grise_validity_years', '10', 'INTEGER', 'CONFORMITE',
     'Duree de validite par defaut d''une carte grise (annees)', 'system');
