-- ---------------------------------------------------------------------
-- Villes des regions Nord-Ouest et Sud-Ouest, plus les localites du
-- corridor de la Route Nationale N3 (Yaounde-Edea-Douala-Limbe-Idenau)
-- et de la Route Nationale N5 (Bekoko-Mbanga-Penja-Nkongsamba-Bafang-
-- Bandjoun) qui manquaient encore au referentiel. La plupart des villes
-- du N3 et du N5 existent deja (Edea, Douala, Yaounde, Mbanga, Penja,
-- Nkongsamba, Bafang, Bandjoun, seedees en V33) : ne restaient a ajouter
-- que Limbe/Idenau (extremite Sud-Ouest du N3) et Bekoko (origine du
-- N5). Meme principe qu'en V33 : idempotent (WHERE NOT EXISTS par nom),
-- pas de quartiers ici, juste les villes pour qu'elles soient
-- selectionnables comme point de livraison depuis l'ecran Missions.
-- ---------------------------------------------------------------------

INSERT INTO cities (code, name, region, latitude, longitude, has_site, active, version, created_at, created_by)
SELECT v.code, v.name, v.region, v.latitude, v.longitude, FALSE, TRUE, 0, now(), 'system'
FROM (VALUES
    -- Nord-Ouest (capitale regionale + les 7 chefs-lieux de departement)
    ('BAM', 'Bamenda',  'Nord-Ouest', 5.9614, 10.1517),
    ('BAL', 'Bali',      'Nord-Ouest', 5.8830, 10.0170),
    ('WUM', 'Wum',       'Nord-Ouest', 6.3830, 10.0670),
    ('FUN', 'Fundong',   'Nord-Ouest', 6.2500, 10.2670),
    ('KBO', 'Kumbo',     'Nord-Ouest', 6.2050, 10.6850),
    ('NKB', 'Nkambé',    'Nord-Ouest', 6.6333, 10.6667),
    ('MBW', 'Mbengwi',   'Nord-Ouest', 6.0170, 10.0000),
    ('NDP', 'Ndop',      'Nord-Ouest', 6.0000, 10.4170),

    -- Sud-Ouest (capitale regionale + les 6 chefs-lieux de departement,
    -- plus Idenau et Tiko, deja des lieux de livraison connus du corridor cotier)
    ('BUE', 'Buea',       'Sud-Ouest', 4.1667, 9.2333),
    ('LIM', 'Limbé',      'Sud-Ouest', 4.0170, 9.2170),
    ('IDE', 'Idenau',     'Sud-Ouest', 4.2330, 8.9830),
    ('TIK', 'Tiko',       'Sud-Ouest', 4.0750, 9.3600),
    ('KUM', 'Kumba',      'Sud-Ouest', 4.6330, 9.4500),
    ('MAM', 'Mamfé',      'Sud-Ouest', 5.7670, 9.2830),
    ('MUN', 'Mundemba',   'Sud-Ouest', 4.9700, 8.9100),
    ('EKT', 'Ekondo Titi','Sud-Ouest', 4.6010, 9.0390),
    ('BGM', 'Bangem',     'Sud-Ouest', 5.0830, 9.7670),
    ('MEN', 'Menji',      'Sud-Ouest', 5.7130, 10.0650),

    -- Littoral : origine de la N5 (Bekoko-Mbanga-Penja-Nkongsamba-Bafang-Bandjoun)
    ('BEK', 'Bekoko',     'Littoral', 4.1137, 9.5795)
) AS v(code, name, region, latitude, longitude)
WHERE NOT EXISTS (SELECT 1 FROM cities c WHERE c.name = v.name);
