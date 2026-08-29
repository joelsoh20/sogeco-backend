package com.sogeco.fleet.modules.vehicle.dto;

import java.math.BigDecimal;

/** Compteurs de tete de l'ecran Gestion des camions. */
public record VehicleStatsResponse(
        long total,
        long enMission,
        long disponible,
        long enMaintenance,
        long enPanne,
        long horsService,
        BigDecimal availabilityRate
) {
}
