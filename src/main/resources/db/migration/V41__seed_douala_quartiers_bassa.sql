-- ---------------------------------------------------------------------
-- Quartiers du secteur Bassa (Douala II/III), signales par l'utilisateur
-- comme manquants pour les livraisons en ville : Nkololoun, Oyak,
-- Brazzaville, Bonadiwoto, Bonanloka et Didom II. Nylon existe deja
-- (V33). Coordonnees verifiees sur OpenStreetMap/Nominatim (meme source
-- que le geocodage de l'application), pas estimees a l'oeil. Idempotent
-- (WHERE NOT EXISTS par ville+nom), comme les autres seeds de villes/quartiers.
-- ---------------------------------------------------------------------

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, v.name, v.latitude, v.longitude, TRUE, 0, now(), 'system'
FROM (VALUES
    ('Nkololoun',  4.0335, 9.7195),
    ('Oyak',       4.0249, 9.7414),
    ('Brazzaville',4.0234, 9.7293),
    ('Bonadiwoto', 4.0162, 9.7217),
    ('Bonanloka',  4.0140, 9.7328),
    ('Didom II',   4.0142, 9.7431)
) AS v(name, latitude, longitude)
CROSS JOIN (SELECT id FROM cities WHERE name = 'Douala' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = v.name
);
