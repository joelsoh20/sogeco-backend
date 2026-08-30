-- Nouveau type de carrosserie : semi-remorque (la remorque elle-meme,
-- sans moteur, tractee par un TRACTEUR).
ALTER TABLE vehicles DROP CONSTRAINT ck_vehicles_body_type;
ALTER TABLE vehicles ADD CONSTRAINT ck_vehicles_body_type CHECK (body_type IN
    ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU', 'UTILITAIRE',
     'MOTO', 'TRICYCLE', 'VOITURE_LIVRAISON', 'SEMI_REMORQUE'));

ALTER TABLE cartes_grises DROP CONSTRAINT ck_cartes_grises_body_type;
ALTER TABLE cartes_grises ADD CONSTRAINT ck_cartes_grises_body_type CHECK (body_type IS NULL OR body_type IN
    ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU', 'UTILITAIRE',
     'MOTO', 'TRICYCLE', 'VOITURE_LIVRAISON', 'SEMI_REMORQUE'));
