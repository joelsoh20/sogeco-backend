-- =====================================================================
-- V24 — Camion rattache a une ville plutot qu'a un site.
--
-- Meme raisonnement que V23 (chauffeur) : la ville determine ou le
-- camion est affecte (Douala, Yaounde, Bafoussam a ce jour), plus
-- simple qu'un site pour ce besoin.
-- =====================================================================

ALTER TABLE vehicles RENAME COLUMN agency_id TO city_id;

ALTER TABLE vehicles DROP CONSTRAINT fk_vehicles_agency;
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicles_city FOREIGN KEY (city_id) REFERENCES cities (id);
