package com.sogeco.fleet.common.enums;

/**
 * Cycle de vie d'une alerte.
 *
 * Le booleen isResolved de l'analyse initiale ne suffisait pas : le
 * taux de resolution et le delai de traitement exigent quatre etats et
 * des horodatages.
 */
public enum AlertStatus {
    NON_RESOLUE,
    EN_COURS,
    RESOLUE,
    IGNOREE;

    public boolean isOpen() {
        return this == NON_RESOLUE || this == EN_COURS;
    }
}
