package com.sogeco.fleet.common.enums;

/** Cycle de vie d'une intervention (RG-7.4). */
public enum MaintenanceStatus {
    PLANIFIEE,
    EN_COURS,
    TERMINEE,
    ANNULEE;

    /** Une intervention en cours immobilise le camion. */
    public boolean immobilizes() {
        return this == EN_COURS;
    }
}
