package com.sogeco.fleet.common.enums;

import java.util.Set;

/**
 * Nature d'un document. Certains types portent une echeance qui alimente
 * l'echeancier ; trois d'entre eux bloquent l'affectation d'une mission
 * lorsqu'ils sont expires (RG-4.5), la liste etant parametrable.
 */
public enum DocumentType {
    CARTE_GRISE,
    CARTE_BLEUE,
    CHRONOTACHYGRAPHE,
    LICENCE_TRANSPORT,
    AUTORISATION_CIRCULER,
    ASSURANCE,
    VISITE_TECHNIQUE,
    PERMIS_CONDUIRE,
    CONTRAT_TRAVAIL,
    PHOTO,
    BON_LIVRAISON,
    ORDRE_MISSION,
    FACTURE,
    RECU_CARBURANT,
    AUTRE;

    private static final Set<DocumentType> WITH_EXPIRY = Set.of(
            CARTE_GRISE, CARTE_BLEUE, CHRONOTACHYGRAPHE, LICENCE_TRANSPORT, AUTORISATION_CIRCULER,
            ASSURANCE, VISITE_TECHNIQUE, PERMIS_CONDUIRE);

    public boolean hasExpiry() {
        return WITH_EXPIRY.contains(this);
    }
}
