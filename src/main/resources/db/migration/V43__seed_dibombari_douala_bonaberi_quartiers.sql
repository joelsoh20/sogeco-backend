-- ---------------------------------------------------------------------
-- Secteur Bekoko-Bonaberi-Dibombari, signale par l'utilisateur (captures
-- centrees sur l'echangeur de Bekoko et la commune de Dibombari, a
-- l'ouest de Douala sur la N3). Coordonnees verifiees sur
-- OpenStreetMap/Nominatim, meme source que le geocodage de
-- l'application.
--
-- Dibombari est une commune a part entiere (chef-lieu d'arrondissement,
-- departement du Moungo) : ajoutee comme ville, avec ses villages en
-- quartiers. Les lieux relevant administrativement de Douala III/IV/V
-- restent rattaches a la ville Douala existante.
--
-- Quelques noms illisibles ou trop partiellement caches sur les
-- captures (ex. "Barengo" deja signale precedemment, "Ndondjo II",
-- "Betayo", "Fundi", "Zusa Bassa", zone "Massoumbou Carrefour") n'ont
-- pas ete recherches : omis plutot que devines. De meme, "Bonebanda"
-- ne remonte sur Nominatim qu'a Tiko (Sud-Ouest), une homonymie qui ne
-- correspond visiblement pas au point indique sur la capture -- omis.
-- ---------------------------------------------------------------------

INSERT INTO cities (code, name, region, latitude, longitude, has_site, active, version, created_at, created_by)
SELECT 'DIB', 'Dibombari', 'Littoral', 4.1785, 9.6558, FALSE, TRUE, 0, now(), 'system'
WHERE NOT EXISTS (SELECT 1 FROM cities WHERE name = 'Dibombari');

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, v.name, v.latitude, v.longitude, TRUE, 0, now(), 'system'
FROM (VALUES
    ('Maka',              4.1367, 9.6030),
    ('Bomono',            4.1394, 9.5946),
    ('Bakoko',            4.1250, 9.5890),
    ('Babenga',           4.1069, 9.5760),
    ('Bwadibo',           4.0925, 9.5826),
    ('Djouki',            4.1427, 9.6210),
    ('Souza',             4.2318, 9.6133),
    ('Grand Souza',       4.2334, 9.6390),
    ('Kombiang Souza',    4.2254, 9.6324),
    ('Bongo',             4.2248, 9.7280),
    ('Bossamba',          4.2211, 9.7545),
    ('Nkapa',             4.2130, 9.6097),
    ('Kasalafam',         4.2219, 9.6089),
    ('Mabanga',           4.2072, 9.6653),
    ('Yangonang',         4.2109, 9.7191),
    ('Bonangando',        4.1526, 9.6670),
    ('Bambou',            4.1616, 9.5634),
    ('Yato',              4.1526, 9.5577),
    ('Bali',              4.1854, 9.6836),
    ('Bonambongué',       4.2054, 9.6964),
    ('Yambé',             4.1974, 9.6969),
    ('Nkendé',            4.2028, 9.6064),
    ('Mbangue II',        4.1844, 9.7042),
    ('Ewoulo Bonambépé',  4.1708, 9.6609),
    ('Yamikoki',          4.1784, 9.6821),
    ('Bomono Gare',       4.1682, 9.5994),
    ('Bonadindé',         4.1483, 9.6288),
    ('Ba Djérou',         4.1562, 9.6226),
    ('Ba Mbengué 1',      4.1346, 9.5927),
    ('Ba Mbengué 2',      4.1499, 9.5892),
    ('Songué',            4.1489, 9.6809),
    ('Moudimapondji',     4.1564, 9.6616),
    ('Mounyoungou',       4.1628, 9.6935),
    ('Mengoumba',         4.2458, 9.5790),
    ('Beyang Mbondjo',    4.2417, 9.5379),
    ('Diwongo',           4.1992, 9.7245),
    ('Yassouka',          4.1935, 9.7271),
    ('Yabakon',           4.1842, 9.7357),
    ('Bonépéa',           4.2347, 9.7769)
) AS v(name, latitude, longitude)
CROSS JOIN (SELECT id FROM cities WHERE name = 'Dibombari' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = v.name
);

INSERT INTO quartiers (city_id, name, latitude, longitude, active, version, created_at, created_by)
SELECT c.id, v.name, v.latitude, v.longitude, TRUE, 0, now(), 'system'
FROM (VALUES
    ('Bepele',                4.1059, 9.6223),
    ('Ndobo',                 4.1018, 9.6360),
    ('Bonendale 1',           4.1122, 9.6376),
    ('Bonendale 2',           4.1164, 9.6544),
    ('Djébalé',               4.1084, 9.6994),
    ('Lobe',                  4.0991, 9.6554),
    ('Minkwélé',              4.1000, 9.6006),
    ('Bonamatoumbe',          4.0952, 9.6711),
    ('Ngwele',                4.0911, 9.6505),
    ('Bonaminkano',           4.0820, 9.6755),
    ('Bonambape',             4.0763, 9.6756),
    ('Washington',            4.0842, 9.6534),
    ('Bojongo',               4.0866, 9.6190),
    ('Besseke',               4.0726, 9.6805),
    ('Bomkoul',               4.0949, 9.8027),
    ('Ngoma',                 4.1142, 9.7881),
    ('Lendi',                 4.1248, 9.7753),
    ('Bayis',                 4.1116, 9.8152),
    ('Bonangang',             4.1056, 9.7445),
    ('Bonagang',              4.0997, 9.7371),
    ('Bonamoussadi Village',  4.1002, 9.7372)
) AS v(name, latitude, longitude)
CROSS JOIN (SELECT id FROM cities WHERE name = 'Douala' LIMIT 1) AS c
WHERE NOT EXISTS (
    SELECT 1 FROM quartiers q WHERE q.city_id = c.id AND q.name = v.name
);
