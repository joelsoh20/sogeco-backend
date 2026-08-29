-- =====================================================================
-- V30 — Compte utilisateur rattache a une ville plutot qu'a un site.
--
-- Meme raisonnement que V23 (chauffeurs) et V24 (camions) : la ville
-- (Douala, Yaounde, Bafoussam a ce jour) determine le perimetre qu'un
-- gestionnaire non-administrateur peut voir et gerer. Le rattachement
-- a une agence n'etait pas exploite pour ce filtrage.
-- =====================================================================

ALTER TABLE users RENAME COLUMN agency_id TO city_id;

ALTER TABLE users DROP CONSTRAINT fk_users_agency;
ALTER TABLE users ADD CONSTRAINT fk_users_city FOREIGN KEY (city_id) REFERENCES cities (id);

ALTER INDEX ix_users_agency RENAME TO ix_users_city;
