-- ---------------------------------------------------------------------
-- Marche Mboppi, signale par l'utilisateur comme destination de
-- livraison quotidienne (boutiques et magasins du marche). Coordonnees
-- verifiees sur OpenStreetMap/Nominatim (meme source que le geocodage
-- de l'application), pas estimees a l'oeil. Idempotent (WHERE NOT
-- EXISTS par ville+nom), comme les autres seeds de villes/quartiers.
-- ---------------------------------------------------------------------

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, 'Marche Mboppi', 4.0467991, 9.7150433, TRUE, 0, now(), 'system'
FROM (SELECT id FROM cities WHERE name = 'Douala' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = 'Marche Mboppi'
);
