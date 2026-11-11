-- Le stock d'huile est tenu par ville : chaque approvisionnement appartient a une ville, et une sortie
-- se deduit du stock de la ville du camion servi. Les achats deja saisis (avant cette migration, ville
-- inconnue) sont rattaches a Douala, a defaut a la premiere ville du referentiel.
ALTER TABLE oil_purchases ADD COLUMN city_id BIGINT;

UPDATE oil_purchases
SET city_id = COALESCE((SELECT id FROM cities WHERE name = 'Douala' LIMIT 1), (SELECT MIN(id) FROM cities))
WHERE city_id IS NULL;

ALTER TABLE oil_purchases ALTER COLUMN city_id SET NOT NULL;
ALTER TABLE oil_purchases
    ADD CONSTRAINT fk_oil_purchase_city FOREIGN KEY (city_id) REFERENCES cities (id);

CREATE INDEX ix_oil_purchases_city ON oil_purchases (city_id, purchase_date);
