package com.sogeco.fleet.modules.maintenance.dto;

import java.math.BigDecimal;

/** Une ligne du bouton "Statistiques par ville" de l'ecran Maintenance. */
public record MaintenanceCityStatsResponse(
        Long cityId,
        String cityName,
        long interventions,
        BigDecimal coutTotal,
        long pannes
) {
}
