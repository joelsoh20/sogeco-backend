-- =====================================================================
-- V23 — Chauffeur rattache a une ville plutot qu'a un site.
--
-- La ville determine ou le chauffeur travaille (Douala, Yaounde,
-- Bafoussam a ce jour) — un decoupage plus simple que le site pour ce
-- besoin, et qui n'exige pas qu'un site existe deja dans la ville.
-- =====================================================================

ALTER TABLE drivers RENAME COLUMN agency_id TO city_id;

ALTER TABLE drivers DROP CONSTRAINT fk_drivers_agency;
ALTER TABLE drivers ADD CONSTRAINT fk_drivers_city FOREIGN KEY (city_id) REFERENCES cities (id);
