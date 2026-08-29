package com.sogeco.fleet.common.enums;

/** Suivi du traitement d'une trame entrante. */
public enum WebhookStatus {
    RECU,
    TRAITE,
    REJETE,
    DOUBLON,
    APPAREIL_INCONNU
}
