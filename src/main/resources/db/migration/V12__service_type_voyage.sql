-- ---------------------------------------------------------------------
-- TYPE DE PRESTATION : VOYAGE HORS VILLE
-- Les 3 types existants (livraison client, reapprovisionnement,
-- transfert) couvrent les mouvements DANS une ville. Il manquait le
-- trajet longue distance ENTRE villes, distinct dans le formulaire
-- Mission : villes de depart/arrivee obligatoires plutot que sites.
-- Non facturable, comme reapprovisionnement/transfert : un voyage
-- hors ville est un deplacement logistique, pas une livraison
-- facturee a un client (qui reste LIVRAISON_CLIENT, quelle que soit
-- la distance parcourue).
-- ---------------------------------------------------------------------
INSERT INTO service_types (code, label, description, billable, created_by) VALUES
    ('VOYAGE_HORS_VILLE', 'Voyage hors ville',
        'Trajet longue distance entre villes, hors deplacement local', FALSE, 'system');
