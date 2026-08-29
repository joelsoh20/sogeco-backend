package com.sogeco.fleet.common.enums;

/**
 * Etat d'un compte utilisateur.
 * Un utilisateur n'est jamais supprime physiquement (RG-13.2).
 */
public enum UserStatus {
    ACTIF,
    SUSPENDU,
    SUPPRIME
}
