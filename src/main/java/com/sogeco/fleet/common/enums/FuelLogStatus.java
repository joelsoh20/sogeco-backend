package com.sogeco.fleet.common.enums;

/**
 * Statut d'un plein (RG-6.6).
 *
 * ANOMALIE est CALCULE par les regles de detection, jamais saisi.
 * ANNULE est manuel et exige un motif.
 */
public enum FuelLogStatus {
    VALIDE,
    ANOMALIE,
    ANNULE
}
