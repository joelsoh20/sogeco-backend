-- Site d'arrivee d'une mission, distinct du site de depart (agency_id existant).
-- Permet un calcul de distance site-a-site, plus precis que ville-a-ville,
-- quand aucun corridor de reference n'est enregistre entre les deux villes.
ALTER TABLE missions ADD COLUMN destination_agency_id BIGINT REFERENCES agencies(id);
