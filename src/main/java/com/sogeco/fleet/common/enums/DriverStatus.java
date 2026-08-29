package com.sogeco.fleet.common.enums;

/** Etat d'un chauffeur (RG-9.2). */
public enum DriverStatus {
    ACTIF,
    EN_CONGE,
    SUSPENDU,
    SORTI;

    public boolean isAssignable() {
        return this == ACTIF;
    }
}
