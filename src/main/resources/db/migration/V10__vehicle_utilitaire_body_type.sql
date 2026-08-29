-- Nouveau type de carrosserie : petit utilitaire / camionnette (livraison urbaine).
ALTER TABLE vehicles DROP CONSTRAINT ck_vehicles_body_type;
ALTER TABLE vehicles ADD CONSTRAINT ck_vehicles_body_type CHECK (body_type IN
    ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU', 'UTILITAIRE'));
