-- ---------------------------------------------------------------------
-- Quartiers/villages peripheriques signales par l'utilisateur (captures
-- d'ecran autour de Bafoussam, marqueur route N6). Coordonnees verifiees
-- sur OpenStreetMap/Nominatim, meme source que le geocodage de
-- l'application. La grande majorite se rattache administrativement a la
-- Communaute Urbaine de Bafoussam (Bafoussam I/II/III) ; Megom depend de
-- Bandjoun (Nkoung-Khi) et Balengou de Bazou (Nde), deux villes deja
-- seedees en V33. "Barengo", partiellement masque par un marqueur sur
-- la capture, n'a pas ete trouve sur Nominatim malgre plusieurs
-- variantes d'orthographe : volontairement omis plutot que devine.
-- Idempotent (WHERE NOT EXISTS par ville+nom), comme les seeds precedents.
-- ---------------------------------------------------------------------

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, v.name, v.latitude, v.longitude, TRUE, 0, now(), 'system'
FROM (VALUES
    ('Njinga I',      5.4870, 10.4763),
    ('Njinga II',     5.4762, 10.4820),
    ('Banefo',        5.4845, 10.5005),
    ('Fou''sap',      5.4723, 10.4594),
    ('Batoukop',      5.4731, 10.4767),
    ('Demsiem',       5.4635, 10.4686),
    ('Ndenbou-Melam', 5.4571, 10.4648),
    ('Tchouo',        5.4627, 10.3840),
    ('Tobang',        5.4531, 10.3874),
    ('Tchouwong',     5.4559, 10.3961),
    ('Mefe',          5.4473, 10.3732),
    ('Tsewong',       5.4468, 10.4002),
    ('Baleng',        5.5173, 10.4101),
    ('Bassinté',      5.5189, 10.4523),
    ('Koptchou II',   5.5167, 10.5077),
    ('Langoueng',     5.5093, 10.4405),
    ('Famtchuèt',     5.5087, 10.4842),
    ('Fantja',        5.4992, 10.4787),
    ('Toungang II',   5.4932, 10.4466)
) AS v(name, latitude, longitude)
CROSS JOIN (SELECT id FROM cities WHERE name = 'Bafoussam' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = v.name
);

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, 'Megom', 5.4428, 10.4144, TRUE, 0, now(), 'system'
FROM (SELECT id FROM cities WHERE name = 'Bandjoun' LIMIT 1) AS c
WHERE NOT EXISTS (SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = 'Megom');

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, 'Balengou', 5.1221, 10.4614, TRUE, 0, now(), 'system'
FROM (SELECT id FROM cities WHERE name = 'Bazou' LIMIT 1) AS c
WHERE NOT EXISTS (SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = 'Balengou');
