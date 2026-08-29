package com.sogeco.fleet.modules.driver.dto;

import java.math.BigDecimal;
import java.util.Map;

/** Compteurs de tete de l'ecran Chauffeurs et Performance. */
public record DriverStatsResponse(
        long total,
        long actifs,
        long enConge,
        long suspendus,
        BigDecimal averagePerformance,
        BigDecimal totalKilometers,
        long totalIncidents,
        long licensesExpiringSoon,
        Map<String, Long> ratingDistribution
) {
}
