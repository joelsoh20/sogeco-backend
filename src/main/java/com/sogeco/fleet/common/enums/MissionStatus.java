package com.sogeco.fleet.common.enums;

import java.util.Set;

/**
 * Cycle de vie d'une mission (RG-5.3 a RG-5.7).
 *
 *   EN_ATTENTE ──demarrage──► EN_COURS ──cloture──► TERMINEE
 *        │                        │
 *        └──────annulation────────┴──► ANNULEE
 *
 * Les transitions autorisees sont portees par l'enum lui-meme : le
 * service n'a plus qu'a interroger canTransitionTo().
 */
public enum MissionStatus {

    EN_ATTENTE,
    EN_COURS,
    TERMINEE,
    ANNULEE;

    public boolean canTransitionTo(MissionStatus target) {
        return switch (this) {
            case EN_ATTENTE -> Set.of(EN_COURS, ANNULEE).contains(target);
            case EN_COURS   -> Set.of(TERMINEE, ANNULEE).contains(target);
            case TERMINEE, ANNULEE -> false;
        };
    }

    public boolean isFinal() {
        return this == TERMINEE || this == ANNULEE;
    }

    /** Une mission active immobilise son camion et son chauffeur. */
    public boolean isActive() {
        return this == EN_ATTENTE || this == EN_COURS;
    }
}
