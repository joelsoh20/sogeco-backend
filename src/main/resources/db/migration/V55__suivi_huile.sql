-- Suivi du fut d'huile moteur (departement logistique) : approvisionnements en huile et huile
-- donnee a chaque camion. Le stock restant = total des approvisionnements - total des sorties.
CREATE TABLE oil_purchases (
    id              BIGSERIAL PRIMARY KEY,
    purchase_date   DATE          NOT NULL,
    quantity_liters NUMERIC(8, 2) NOT NULL,
    note            VARCHAR(200),

    version         INTEGER       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(150),
    updated_by      VARCHAR(150),

    CONSTRAINT ck_oil_purchase_qty CHECK (quantity_liters > 0)
);

CREATE INDEX ix_oil_purchases_date ON oil_purchases (purchase_date);

CREATE TABLE oil_distributions (
    id                BIGSERIAL PRIMARY KEY,
    vehicle_id        BIGINT        NOT NULL,
    distribution_date DATE          NOT NULL,
    quantity_liters   NUMERIC(8, 2) NOT NULL,
    -- Chauffeur ou personne qui a recu l'huile (repris du chauffeur affecte si non saisi).
    recipient_name    VARCHAR(150),
    note              VARCHAR(200),

    version           INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(150),
    updated_by        VARCHAR(150),

    CONSTRAINT fk_oil_distribution_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT ck_oil_distribution_qty CHECK (quantity_liters > 0)
);

CREATE INDEX ix_oil_distributions_date    ON oil_distributions (distribution_date);
CREATE INDEX ix_oil_distributions_vehicle ON oil_distributions (vehicle_id, distribution_date);
