package com.sogeco.fleet.common.enums;

/**
 * Postes de depense hors carburant et maintenance.
 * Correspond a la repartition des couts du tableau de bord.
 */
public enum ExpenseCategory {
    SALAIRE,
    PEAGE,
    AMENDE,
    ADMINISTRATIF,
    ASSURANCE,
    MANUTENTION,
    AUTRE;

    /** Depenses imputables a une mission precise. */
    public boolean isMissionRelated() {
        return this == PEAGE || this == MANUTENTION || this == AMENDE;
    }
}
