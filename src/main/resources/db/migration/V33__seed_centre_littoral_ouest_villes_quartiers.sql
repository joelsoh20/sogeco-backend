-- ---------------------------------------------------------------------
-- Villes et quartiers des regions Centre, Littoral et Ouest.
-- Le referentiel villes/quartiers etant ouvert (cree a la volee depuis
-- l'ecran Missions), la production ne contenait jusqu'ici que les 3
-- villes ou l'entreprise a un site (Douala, Yaounde, Bafoussam) : tout
-- autre point de livraison devait etre saisi/geocode a la main avant de
-- pouvoir etre choisi pour un voyage. Cette migration precharge les
-- communes reelles des 3 regions, pour qu'elles soient selectionnables
-- immediatement. Idempotent (WHERE NOT EXISTS par nom) : ne duplique
-- rien la ou une ville/un quartier existe deja (cree ad hoc).
-- ---------------------------------------------------------------------

-- Les 3 villes d'implantation (Douala/Yaounde/Bafoussam) existent deja partout ou
-- l'appli a deja servi (creees ad hoc a la premiere mission), mais pas forcement sur
-- un environnement tout neuf : sans elles ici, les quartiers ci-dessous n'auraient
-- aucune ville sur laquelle se rattacher.
INSERT INTO cities (code, name, region, latitude, longitude, has_site, active, version, created_at, created_by)
SELECT v.code, v.name, v.region, v.latitude, v.longitude, TRUE, TRUE, 0, now(), 'system'
FROM (VALUES
    ('DOU', 'Douala',    'Littoral', 4.0511, 9.7679),
    ('YAO', 'Yaoundé',   'Centre',   3.8480, 11.5021),
    ('BAF', 'Bafoussam', 'Ouest',    5.4737, 10.4176)
) AS v(code, name, region, latitude, longitude)
WHERE NOT EXISTS (SELECT 1 FROM cities c WHERE c.name = v.name);

INSERT INTO cities (code, name, region, latitude, longitude, has_site, active, version, created_at, created_by)
SELECT v.code, v.name, v.region, v.latitude, v.longitude, FALSE, TRUE, 0, now(), 'system'
FROM (VALUES
    -- Centre
    ('AKO',  'Akonolinga',  'Centre', 3.7667,  12.2500),
    ('AYO',  'Ayos',        'Centre', 3.9000,  12.5167),
    ('BAF2', 'Bafia',       'Centre', 4.7500,  11.2333),
    ('BOK',  'Bokito',      'Centre', 4.5667,  11.1167),
    ('ESK',  'Eséka',       'Centre', 3.6500,  10.7667),
    ('MAK',  'Makak',       'Centre', 3.5833,  10.9833),
    ('MAT',  'Matomb',      'Centre', 3.9333,  10.6667),
    ('MBA1', 'Mbalmayo',    'Centre', 3.5167,  11.5000),
    ('MFO',  'Mfou',        'Centre', 3.7167,  11.6333),
    ('MON',  'Monatélé',    'Centre', 4.3167,  11.2000),
    ('NAN',  'Nanga-Eboko', 'Centre', 4.6833,  12.3667),
    ('NGO',  'Ngoumou',     'Centre', 3.6167,  11.3167),
    ('NKT',  'Nkoteng',     'Centre', 4.5167,  12.0333),
    ('NTU',  'Ntui',        'Centre', 4.4500,  11.6333),
    ('OBA',  'Obala',       'Centre', 4.1667,  11.5333),
    ('OMB',  'Ombessa',     'Centre', 4.7000,  11.2667),
    ('SAA',  'Sa''a',       'Centre', 4.3833,  11.3833),
    ('SOA',  'Soa',         'Centre', 3.9333,  11.5833),
    ('YOK',  'Yoko',        'Centre', 5.5333,  12.3167),

    -- Littoral
    ('DIZ',  'Dizangué',    'Littoral', 3.6833, 9.9500),
    ('EDA',  'Edéa',        'Littoral', 3.7981, 10.1320),
    ('LOU',  'Loum',        'Littoral', 4.7167, 9.7333),
    ('MAN',  'Manjo',       'Littoral', 4.8333, 9.8167),
    ('MBA',  'Mbanga',      'Littoral', 4.5167, 9.5667),
    ('MEL',  'Melong',      'Littoral', 5.1167, 9.9500),
    ('NGA',  'Ngambé',      'Littoral', 3.9500, 10.6500),
    ('NJP',  'Njombé-Penja','Littoral', 4.8667, 9.6667),
    ('NKO1', 'Nkondjock',   'Littoral', 4.7667, 10.2333),
    ('NKO',  'Nkongsamba',  'Littoral', 4.9547, 9.9401),
    ('POU',  'Pouma',       'Littoral', 3.8667, 10.4667),
    ('YAB',  'Yabassi',     'Littoral', 4.4333, 9.9667),

    -- Ouest
    ('BAF1', 'Bafang',      'Ouest', 5.1667, 10.1833),
    ('BAH',  'Baham',       'Ouest', 5.3000, 10.3667),
    ('BAB',  'Babadjou',    'Ouest', 5.6833, 10.2167),
    ('BMJ',  'Bamendjou',   'Ouest', 5.3167, 10.3167),
    ('BAN2', 'Bandja',      'Ouest', 5.2333, 10.1000),
    ('BAN',  'Bandjoun',    'Ouest', 5.3667, 10.4167),
    ('BAN1', 'Bangangté',   'Ouest', 5.1500, 10.5167),
    ('BGR',  'Bangourain',  'Ouest', 5.8833, 10.8500),
    ('BAT',  'Batié',       'Ouest', 5.2000, 10.2667),
    ('BTC',  'Batcham',     'Ouest', 5.7333, 10.2667),
    ('BAZ',  'Bazou',       'Ouest', 5.0500, 10.6500),
    ('DSC',  'Dschang',     'Ouest', 5.4500, 10.0667),
    ('FOK',  'Fokoué',      'Ouest', 5.4667, 10.1667),
    ('FOU',  'Foumban',     'Ouest', 5.7267, 10.9000),
    ('FOU1', 'Foumbot',     'Ouest', 5.5083, 10.6333),
    ('GAL',  'Galim',       'Ouest', 5.6833, 10.0500),
    ('KKE',  'Kékem',       'Ouest', 5.0500, 9.9333),
    ('KTB',  'Koutaba',     'Ouest', 5.6167, 10.7833),
    ('KTM',  'Kouoptamo',   'Ouest', 5.5500, 10.5833),
    ('MAL',  'Malantouen',  'Ouest', 5.6333, 10.7500),
    ('MAG',  'Magba',       'Ouest', 6.2500, 11.3167),
    ('MAS',  'Massangam',   'Ouest', 5.7333, 10.9333),
    ('MBO',  'Mbouda',      'Ouest', 5.6333, 10.2500),
    ('SAN',  'Santchou',    'Ouest', 5.3833, 9.9000),
    ('TON',  'Tonga',       'Ouest', 5.1500, 10.5833)
) AS v(code, name, region, latitude, longitude)
WHERE NOT EXISTS (SELECT 1 FROM cities c WHERE c.name = v.name);

-- Une ville deja creee ad hoc (sans region renseignee) recoit sa region si elle
-- fait partie des 3 sites d'implantation.
UPDATE cities SET region = 'Littoral' WHERE name = 'Douala'    AND region IS NULL;
UPDATE cities SET region = 'Centre'   WHERE name = 'Yaoundé'   AND region IS NULL;
UPDATE cities SET region = 'Centre'   WHERE name = 'Yaounde'   AND region IS NULL;
UPDATE cities SET region = 'Ouest'    WHERE name = 'Bafoussam' AND region IS NULL;

-- Quartiers de Douala (deja largement geocodes ad hoc dans l'usage reel de
-- l'ecran Missions) : reproduits ici pour que tout environnement (dont la
-- production) en beneficie sans attendre qu'ils soient recrees un a un.
INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, v.name, v.latitude, v.longitude, TRUE, 0, now(), 'system'
FROM (VALUES
    ('Douala', 'Akwa',         4.0483, 9.7003),
    ('Douala', 'Bali',         4.0510, 9.6920),
    ('Douala', 'Bassa',        4.0610, 9.7160),
    ('Douala', 'Bepanda',      4.0650, 9.7300),
    ('Douala', 'Bonaberi',     4.0700, 9.6700),
    ('Douala', 'Bonanjo',      4.0473, 9.7284),
    ('Douala', 'Bonapriso',    4.0350, 9.7000),
    ('Douala', 'Bonassama',    4.0850, 9.6600),
    ('Douala', 'Camp Yabassi', 4.0432, 9.7092),
    ('Douala', 'Cité SIC',     4.0600, 9.7400),
    ('Douala', 'Deido',        4.0650, 9.7050),
    ('Douala', 'Japoma',       4.0450, 9.8200),
    ('Douala', 'Kotto',        4.0450, 9.7650),
    ('Douala', 'Logbaba',      4.0300, 9.7600),
    ('Douala', 'Madagascar',   4.0505, 9.7255),
    ('Douala', 'Makepe',       4.0700, 9.7450),
    ('Douala', 'Ndogbong',     4.0750, 9.7350),
    ('Douala', 'Ndogsimbi',    4.0404, 9.7312),
    ('Douala', 'Ndokoti',      4.0434, 9.7437),
    ('Douala', 'New-Bell',     4.0567, 9.7150),
    ('Douala', 'Nyalla',       4.0100, 9.7500),
    ('Douala', 'Nylon',        4.0450, 9.7200),
    ('Douala', 'PK8',          4.0100, 9.6800),
    ('Douala', 'PK10',         4.0000, 9.6650),
    ('Douala', 'PK12',         3.9900, 9.6500),
    ('Douala', 'PK14',         3.9800, 9.6350),
    ('Douala', 'TMP',          4.0603, 9.7837),
    ('Douala', 'Village',      4.0000, 9.7400),
    ('Douala', 'Yassa',        4.0250, 9.7850),

    -- Yaounde
    ('Yaoundé', 'Bastos',        3.8917, 11.5194),
    ('Yaoundé', 'Centre-ville',  3.8667, 11.5167),
    ('Yaoundé', 'Mvog-Mbi',      3.8500, 11.5167),
    ('Yaoundé', 'Mvog-Ada',      3.8583, 11.5083),
    ('Yaoundé', 'Nlongkak',      3.8833, 11.5167),
    ('Yaoundé', 'Elig-Essono',   3.8750, 11.5083),
    ('Yaoundé', 'Essos',         3.8833, 11.5250),
    ('Yaoundé', 'Mokolo',        3.8750, 11.5083),
    ('Yaoundé', 'Biyem-Assi',    3.8333, 11.4917),
    ('Yaoundé', 'Ngousso',       3.8833, 11.5417),
    ('Yaoundé', 'Nkoldongo',     3.8583, 11.5333),
    ('Yaoundé', 'Etoudi',        3.9167, 11.5333),
    ('Yaoundé', 'Nsimalen',      3.7167, 11.5500),
    ('Yaoundé', 'Nkolbisson',    3.8833, 11.4500),
    ('Yaoundé', 'Mendong',       3.8250, 11.4667),
    ('Yaoundé', 'Efoulan',       3.8417, 11.4917),
    ('Yaoundé', 'Emana',         3.9333, 11.5167),
    ('Yaoundé', 'Odza',          3.8167, 11.5333),
    ('Yaoundé', 'Ekounou',       3.8500, 11.5417),
    ('Yaoundé', 'Messa',         3.8750, 11.4917),
    ('Yaoundé', 'Melen',         3.8583, 11.4750),
    ('Yaoundé', 'Ngoa-Ekelle',   3.8583, 11.5083),
    ('Yaoundé', 'Simbock',       3.8083, 11.5000),
    ('Yaoundé', 'Awae',          3.8500, 11.5750),

    -- Bafoussam
    ('Bafoussam', 'Tamdja',      5.4767, 10.4183),
    ('Bafoussam', 'Kamkop',      5.4633, 10.4367),
    ('Bafoussam', 'Djeleng',     5.4550, 10.4083),
    ('Bafoussam', 'Banengo',     5.4900, 10.4067),
    ('Bafoussam', 'Famla',       5.4783, 10.4467),
    ('Bafoussam', 'Toungang I',  5.4900, 10.4267),
    ('Bafoussam', 'Kena',        5.4667, 10.4133),
    ('Bafoussam', 'Tyo-Ville',   5.4700, 10.4200),
    ('Bafoussam', 'Marché A',    5.4750, 10.4200),
    ('Bafoussam', 'Marché B',    5.4700, 10.4250)
) AS v(city_name, name, latitude, longitude)
JOIN cities c ON c.name = v.city_name
WHERE NOT EXISTS (SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = v.name);
