-- ---------------------------------------------------------------------
-- Marche A, quartier de Bafoussam ou l'utilisateur cree un nouveau
-- site (siege/agence/depot) sur la carte. Coordonnees verifiees sur
-- OpenStreetMap/Nominatim (meme source que le geocodage de
-- l'application), pas estimees a l'oeil. Idempotent (WHERE NOT EXISTS
-- par ville+nom), comme les autres seeds de villes/quartiers.
-- ---------------------------------------------------------------------

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, 'Marche A', 5.4789703, 10.4171312, TRUE, 0, now(), 'system'
FROM (SELECT id FROM cities WHERE name = 'Bafoussam' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = 'Marche A'
);
