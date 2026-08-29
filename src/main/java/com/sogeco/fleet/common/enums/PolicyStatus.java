package com.sogeco.fleet.common.enums;

/** Cycle de vie d'un contrat d'assurance. */
public enum PolicyStatus {
    ACTIVE,
    EXPIREE,
    RESILIEE;

    public boolean isCurrent() {
        return this == ACTIVE;
    }
}
