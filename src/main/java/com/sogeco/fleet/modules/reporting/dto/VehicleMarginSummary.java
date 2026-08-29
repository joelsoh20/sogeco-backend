package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

/** Ligne courte pour les classements du tableau de bord executif. */
public record VehicleMarginSummary(
        Long vehicleId, String registrationNumber, BigDecimal margin, long missionCount) {
}
