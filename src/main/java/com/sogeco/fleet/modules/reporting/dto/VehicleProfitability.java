package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

/**
 * Rentabilite d'un camion sur une periode.
 *
 * directMargin = recette - couts directs de mission (carburant,
 * peages, quote-part chauffeur) — c'est Mission.marginAmount agrege.
 *
 * netMargin = directMargin - maintenance. La maintenance n'est jamais
 * rattachee a une mission precise, c'est un cout periodique du
 * camion : l'ignorer surestimerait la rentabilite reelle.
 */
public record VehicleProfitability(
        Long vehicleId,
        String registrationNumber,
        long missionCount,
        BigDecimal totalRevenue,
        BigDecimal directCost,
        BigDecimal directMargin,
        BigDecimal maintenanceCost,
        BigDecimal netMargin,
        BigDecimal netMarginPercent,
        BigDecimal kmDriven,
        BigDecimal costPerKm
) {
}
