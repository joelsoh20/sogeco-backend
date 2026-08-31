-- ---------------------------------------------------------------------
-- Corrige les coordonnees approximatives introduites par V33 pour les
-- communes/quartiers sans donnee geocodee prealable (Yoko, Magba,
-- quartiers de Yaounde...). Verifie en pratique : OpenRouteService
-- refuse ces points ("Could not find routable point within a radius of
-- 350m"), le trajet retombe alors sur l'estimation a vol d'oiseau au
-- lieu de la distance routiere reelle. Coordonnees de remplacement
-- obtenues par geocodage reel (Nominatim/OSM), verifiees a l'unite.
-- ---------------------------------------------------------------------

UPDATE cities SET latitude = 5.5406812, longitude = 12.3186341 WHERE name = 'Yoko';
UPDATE cities SET latitude = 4.5046903, longitude = 12.1187119 WHERE name = 'Nkoteng';
UPDATE cities SET latitude = 4.5867240, longitude = 9.6529320  WHERE name = 'Njombé-Penja';
UPDATE cities SET latitude = 5.6786237, longitude = 10.2050757 WHERE name = 'Babadjou';
UPDATE cities SET latitude = 5.5782259, longitude = 10.2018416 WHERE name = 'Batcham';
UPDATE cities SET latitude = 5.6909862, longitude = 10.3662130 WHERE name = 'Galim';
UPDATE cities SET latitude = 5.3894025, longitude = 10.3312054 WHERE name = 'Bamendjou';
UPDATE cities SET latitude = 5.6511460, longitude = 10.7609170 WHERE name = 'Koutaba';
UPDATE cities SET latitude = 5.9657420, longitude = 11.2251710 WHERE name = 'Magba';
UPDATE cities SET latitude = 5.4258505, longitude = 11.0018554 WHERE name = 'Massangam';
UPDATE cities SET latitude = 4.9694090, longitude = 10.6993080 WHERE name = 'Tonga';
UPDATE cities SET latitude = 5.9041600, longitude = 10.6602620 WHERE name = 'Bangourain';
UPDATE cities SET latitude = 5.6557267, longitude = 10.6122387 WHERE name = 'Kouoptamo';

UPDATE quartiers q SET latitude = v.latitude, longitude = v.longitude
FROM (VALUES
    ('Bastos',        3.8940184, 11.5108818),
    ('Mvog-Mbi',      3.8513998, 11.5202857),
    ('Mvog-Ada',      3.8635231, 11.5283875),
    ('Nlongkak',      3.8870052, 11.5189660),
    ('Elig-Essono',   3.8775533, 11.5229540),
    ('Essos',         3.8758976, 11.5434531),
    ('Mokolo',        3.8756258, 11.4984148),
    ('Biyem-Assi',    3.8404702, 11.4864781),
    ('Ngousso',       3.9026206, 11.5490798),
    ('Nkoldongo',     3.8560185, 11.5272655),
    ('Etoudi',        3.9149771, 11.5252810),
    ('Nsimalen',      3.7996452, 11.5019715),
    ('Nkolbisson',    3.8745876, 11.4525933),
    ('Mendong',       3.8346435, 11.4726985),
    ('Efoulan',       3.8343538, 11.5064393),
    ('Emana',         3.9328292, 11.5223295),
    ('Odza',          3.7985968, 11.5291189),
    ('Ekounou',       3.8416153, 11.5340896),
    ('Messa',         3.8875800, 11.4775200),
    ('Melen',         3.8645891, 11.4963994),
    ('Ngoa-Ekelle',   3.8595668, 11.5053794),
    ('Simbock',       3.8098662, 11.4761718),
    ('Awae',          3.8359900, 11.5505661)
) AS v(name, latitude, longitude)
WHERE q.name = v.name AND q.city_id = (SELECT id FROM cities WHERE name = 'Yaoundé');

UPDATE quartiers q SET latitude = v.latitude, longitude = v.longitude
FROM (VALUES
    ('Tamdja',    5.4671160, 10.4228594),
    ('Kamkop',    5.5096191, 10.3793874),
    ('Djeleng',   5.4832169, 10.4155971),
    ('Banengo',   5.4643101, 10.4167828),
    ('Famla',     5.4777340, 10.4194794),
    ('Tyo-Ville', 5.4877804, 10.4091506)
) AS v(name, latitude, longitude)
WHERE q.name = v.name AND q.city_id = (SELECT id FROM cities WHERE name = 'Bafoussam');

-- "Toungang I" et "Kena" (Bafoussam) : aucune coordonnee fiable trouvee au
-- geocodage (distincte de "Toungang II", deja connue et correcte) — plutot
-- que de laisser une approximation qui echouerait au routage comme les
-- autres ci-dessus, on les desactive en attendant un pointage reel (clic
-- sur la carte, cf. ecran Missions).
UPDATE quartiers SET active = FALSE
WHERE name IN ('Toungang I', 'Kena') AND city_id = (SELECT id FROM cities WHERE name = 'Bafoussam');
