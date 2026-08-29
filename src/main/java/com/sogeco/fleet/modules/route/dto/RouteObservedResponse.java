package com.sogeco.fleet.modules.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Moyennes constatees sur les missions terminees du corridor.
 * Alimente le bouton "calculer depuis l'historique" du formulaire.
 */
@Schema(description = "Valeurs observees, proposees comme reference")
public record RouteObservedResponse(
        long missionCount,
        BigDecimal observedDistanceKm,
        Integer observedDurationMinutes,
        BigDecimal currentReferenceDistanceKm,
        BigDecimal deviationPercent,
        String warning
) {
}
