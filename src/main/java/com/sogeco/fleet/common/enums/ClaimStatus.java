package com.sogeco.fleet.common.enums;

/** Cycle de vie d'un sinistre. */
public enum ClaimStatus {
    DECLARE,
    EN_INSTRUCTION,
    ACCEPTE,
    REFUSE,
    CLOTURE;

    public boolean isOpen() {
        return this != CLOTURE && this != REFUSE;
    }
}
