-- ---------------------------------------------------------------------
-- Persiste la distance GPS retenue pour chaque plein (jusqu'ici une
-- simple variable locale de FuelService, jamais sauvegardee). Necessaire
-- pour calculer une moyenne de consommation par somme (litres/km) plutot
-- que par moyenne simple des ratios par plein -- cette derniere se
-- laisse fausser des qu'un plein est partiel (frequent en pratique,
-- notamment sur les camions qu'on ne remplit jamais a fond), car un
-- petit ravitaillement sur une courte distance produit un ratio tres
-- bruite qui pese alors autant qu'un plein sur une longue distance.
-- ---------------------------------------------------------------------

ALTER TABLE fuel_logs ADD COLUMN distance_km NUMERIC(10,3);

UPDATE fuel_logs
SET distance_km = ROUND(quantity_liters * 100 / computed_consumption, 3)
WHERE computed_consumption IS NOT NULL AND computed_consumption <> 0;
