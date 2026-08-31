-- ---------------------------------------------------------------------
-- Siege social (Direction Generale) a Douala, quartier Carrefour BP
-- Cite / Ndogbati -- distinct du "Depot Douala" deja enregistre au
-- rond-point voisin. Coordonnees approximatives (lecture d'une capture
-- d'ecran, pas d'adresse geocodee precise) : a corriger si besoin.
-- ---------------------------------------------------------------------

INSERT INTO agencies (code, name, city_id, site_type, address, latitude, longitude, active, version, created_at, created_by)
SELECT 'DOU-SIE', 'Direction Générale', c.id, 'SIEGE', 'Carrefour BP Cité, Bassa, Douala',
       4.0492000, 9.7266000, TRUE, 0, now(), 'system'
FROM cities c
WHERE c.name = 'Douala'
  AND NOT EXISTS (SELECT 1 FROM agencies a WHERE a.code = 'DOU-SIE');
