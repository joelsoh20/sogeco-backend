-- ---------------------------------------------------------------------
-- SITE <-> QUARTIER
-- Un site (siege/agence/depot) peut desormais preciser son quartier,
-- en plus de sa ville : la localisation la plus fine disponible pour
-- geocoder le site et affiner les distances de mission calculees a
-- partir de ce site (voir MissionService.resolveCoordinates).
-- Nullable : les sites deja crees avant cette colonne n'ont pas de
-- quartier renseigne — seule la creation de NOUVEAUX sites l'exige
-- desormais (contrainte applicative, pas de contrainte NOT NULL ici).
-- ---------------------------------------------------------------------
ALTER TABLE agencies ADD COLUMN quartier_id BIGINT REFERENCES quartiers(id);

CREATE INDEX ix_agencies_quartier ON agencies (quartier_id);
