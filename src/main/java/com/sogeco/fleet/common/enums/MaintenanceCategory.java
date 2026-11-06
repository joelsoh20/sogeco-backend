package com.sogeco.fleet.common.enums;

/** Categories de la repartition des couts (maquette Maintenance). */
public enum MaintenanceCategory {
    ENTRETIEN_PREVENTIF,
    REPARATION_MECANIQUE,
    PNEUMATIQUE,
    ELECTRICITE,
    LAVERIE,
    AUTRES;

    /**
     * Reparations qui ne se font que sur un camion declare en panne (ou deja a l'atelier) :
     * la laverie, les autres prestations et l'entretien preventif n'empechent pas de rouler.
     */
    public boolean requiresBreakdown() {
        return this == REPARATION_MECANIQUE || this == PNEUMATIQUE || this == ELECTRICITE;
    }

    /** Seul l'entretien preventif compte dans le ratio de maturite. */
    public boolean isPreventive() {
        return this == ENTRETIEN_PREVENTIF;
    }
}
