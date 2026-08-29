package com.sogeco.fleet.common.enums;

/**
 * Usage d'un camion : longue distance ou rotations locales.
 *
 * TOUR_VILLE ouvre droit a un lavage hebdomadaire automatique (chaque
 * samedi), deduit comme charge de maintenance — voir LaverieScheduler.
 */
public enum UsageType {
    VOYAGE,
    TOUR_VILLE
}
