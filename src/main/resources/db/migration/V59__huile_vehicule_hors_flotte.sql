-- Huile donnee a un vehicule qui n'est pas de la flotte (ou d'une autre ville) : saisi par son immatriculation.
-- La sortie porte maintenant sa propre ville de stock (celle d'ou l'huile est prise) : pour un camion de la
-- flotte c'est sa ville, pour un vehicule hors flotte c'est la ville choisie par celui qui donne l'huile.
ALTER TABLE oil_distributions ADD COLUMN city_id BIGINT;
ALTER TABLE oil_distributions ADD COLUMN external_plate VARCHAR(30);

UPDATE oil_distributions d
SET city_id = COALESCE(
        (SELECT v.city_id FROM vehicles v WHERE v.id = d.vehicle_id),
        (SELECT id FROM cities WHERE name = 'Douala' LIMIT 1),
        (SELECT MIN(id) FROM cities))
WHERE d.city_id IS NULL;

ALTER TABLE oil_distributions ALTER COLUMN city_id SET NOT NULL;
ALTER TABLE oil_distributions
    ADD CONSTRAINT fk_oil_distribution_city FOREIGN KEY (city_id) REFERENCES cities (id);

ALTER TABLE oil_distributions ALTER COLUMN vehicle_id DROP NOT NULL;
ALTER TABLE oil_distributions
    ADD CONSTRAINT ck_oil_distribution_target
        CHECK (vehicle_id IS NOT NULL OR (external_plate IS NOT NULL AND length(trim(external_plate)) > 0));

CREATE INDEX ix_oil_distributions_city ON oil_distributions (city_id, distribution_date);
