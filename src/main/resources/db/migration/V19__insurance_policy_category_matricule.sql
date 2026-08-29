-- =====================================================================
-- V19 — Police d'assurance : categorie de vehicule et matricule saisi
--        librement ("Genre"), a la place de la liste de camions coches.
--
-- La relation policy_vehicles (RG-4.5, blocage d'affectation si police
-- expiree) est conservee : quand le matricule saisi correspond a un
-- camion existant, le service le relie automatiquement en arriere-plan.
-- Sans correspondance (camion pas encore enregistre), la police existe
-- quand meme, simplement sans effet bloquant tant que le camion n'est
-- pas ajoute avec la meme immatriculation.
-- =====================================================================

ALTER TABLE insurance_policies ADD COLUMN category VARCHAR(20);
ALTER TABLE insurance_policies ADD COLUMN vehicle_registration VARCHAR(20);

ALTER TABLE insurance_policies ADD CONSTRAINT ck_policies_category CHECK (category IS NULL OR category IN
    ('TRACTEUR', 'PORTEUR', 'BENNE', 'CITERNE', 'FOURGON', 'PLATEAU', 'UTILITAIRE'));
