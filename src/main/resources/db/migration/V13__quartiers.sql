-- ---------------------------------------------------------------------
-- QUARTIERS
-- Referentiel ouvert, comme les villes (cities) : tout quartier de
-- livraison rencontre y est cree depuis l'ecran Missions, avec ses
-- coordonnees geocodees automatiquement si elles ne sont pas saisies.
-- Sert a affiner l'estimation de distance d'un voyage/livraison
-- au-dela du seul centre-ville.
-- ---------------------------------------------------------------------
CREATE TABLE quartiers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100)  NOT NULL,
    city_id         BIGINT        NOT NULL REFERENCES cities(id),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted_at      TIMESTAMPTZ,

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT uk_quartiers_name_city UNIQUE (city_id, name),
    CONSTRAINT ck_quartiers_latitude  CHECK (latitude  IS NULL OR (latitude  BETWEEN -90  AND 90)),
    CONSTRAINT ck_quartiers_longitude CHECK (longitude IS NULL OR (longitude BETWEEN -180 AND 180))
);

CREATE INDEX ix_quartiers_city   ON quartiers (city_id);
CREATE INDEX ix_quartiers_active ON quartiers (active);
