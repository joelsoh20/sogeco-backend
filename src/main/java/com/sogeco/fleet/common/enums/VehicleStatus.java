package com.sogeco.fleet.common.enums;

/**
 * Etat d'exploitation d'un camion (RG-4.2).
 * Le passage en EN_MISSION est automatique au demarrage d'une mission,
 * jamais saisi manuellement (RG-4.3).
 */
public enum VehicleStatus {
    DISPONIBLE,
    EN_MISSION,
    EN_MAINTENANCE,
    EN_PANNE,
    HORS_SERVICE;

    /** Seul un camion disponible peut recevoir une mission (RG-4.4). */
    public boolean isAssignable() {
        return this == DISPONIBLE;
    }
}
