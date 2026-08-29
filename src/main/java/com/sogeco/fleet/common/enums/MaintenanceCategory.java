package com.sogeco.fleet.common.enums;

/** Categories de la repartition des couts (maquette Maintenance). */
public enum MaintenanceCategory {
    ENTRETIEN_PREVENTIF,
    REPARATION_MECANIQUE,
    PNEUMATIQUE,
    ELECTRICITE,
    LAVERIE,
    AUTRES;

    /** Seul l'entretien preventif compte dans le ratio de maturite. */
    public boolean isPreventive() {
        return this == ENTRETIEN_PREVENTIF;
    }
}
