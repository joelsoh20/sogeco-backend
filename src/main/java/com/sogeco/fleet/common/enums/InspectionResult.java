package com.sogeco.fleet.common.enums;

/** Issue d'une visite technique. */
public enum InspectionResult {
    CONFORME,
    NON_CONFORME,
    CONFORME_AVEC_RESERVES;

    /** Un controle non conforme immobilise le camion (RG-8.5). */
    public boolean blocksVehicle() {
        return this == NON_CONFORME;
    }
}
