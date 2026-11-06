-- ---------------------------------------------------------------------
-- Villes des regions Sud et Est. Pour un voyage hors ville, la destination
-- se choisit par le seul nom de la ville, parmi les 7 regions desservies :
-- Ouest, Littoral, Centre, Nord-Ouest, Sud-Ouest, Sud et Est. Les 5
-- premieres sont deja chargees (V33 et suivantes) ; Sud et Est n'avaient
-- aucune ville. Kribi existait sans region : rattachee au Sud.
-- Idempotent (WHERE NOT EXISTS par nom).
-- ---------------------------------------------------------------------

UPDATE cities SET region = 'Sud' WHERE name = 'Kribi' AND region IS NULL;

INSERT INTO cities (code, name, region, latitude, longitude, has_site, active, version, created_at, created_by)
SELECT v.code, v.name, v.region, v.latitude, v.longitude, FALSE, TRUE, 0, now(), 'system'
FROM (VALUES
    -- Sud
    ('EBO', 'Ebolowa',    'Sud', 2.9000, 11.1500),
    ('KRI', 'Kribi',      'Sud', 2.9400,  9.9100),
    ('SMA', 'Sangmélima', 'Sud', 2.9333, 11.9833),
    ('AMB', 'Ambam',      'Sud', 2.3833, 11.2833),
    ('CAM', 'Campo',      'Sud', 2.3667,  9.8167),
    ('LOL', 'Lolodorf',   'Sud', 3.2333, 10.7333),
    ('DJO', 'Djoum',      'Sud', 2.6667, 12.6667),
    ('ZOE', 'Zoétélé',    'Sud', 3.2500, 11.9167),
    ('MAA', 'Ma''an',     'Sud', 2.3667, 10.6167),
    -- Est
    ('BRT', 'Bertoua',        'Est', 4.5833, 13.6833),
    ('BTR', 'Batouri',        'Est', 4.4333, 14.3667),
    ('ABM', 'Abong-Mbang',    'Est', 3.9833, 13.1667),
    ('YKD', 'Yokadouma',      'Est', 3.5167, 15.0500),
    ('BLB', 'Bélabo',         'Est', 4.9333, 13.3000),
    ('GBL', 'Garoua-Boulaï',  'Est', 5.8833, 14.5500),
    ('LMI', 'Lomié',          'Est', 3.1667, 13.6167),
    ('MLD', 'Moloundou',      'Est', 2.0500, 15.2167),
    ('DOM', 'Doumé',          'Est', 4.2333, 13.4500),
    ('BOY', 'Bétaré-Oya',     'Est', 5.5833, 14.0833)
) AS v(code, name, region, latitude, longitude)
WHERE NOT EXISTS (SELECT 1 FROM cities c WHERE c.name = v.name)
  AND NOT EXISTS (SELECT 1 FROM cities c WHERE c.code = v.code);
