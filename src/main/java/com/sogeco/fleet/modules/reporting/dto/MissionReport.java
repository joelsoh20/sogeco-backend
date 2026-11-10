package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Rapport des missions effectuees (terminees) par un ou plusieurs camions sur une
 * periode : semaine, mois ou dates libres. Une section par camion, missions du plus
 * ancien au plus recent.
 */
public record MissionReport(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        String generatedBy,
        long missionCount,
        BigDecimal totalKm,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin,
        List<VehicleMissions> vehicles
) {

    public record VehicleMissions(
            Long vehicleId,
            String registrationNumber,
            String description,
            long missionCount,
            BigDecimal totalKm,
            BigDecimal totalRevenue,
            BigDecimal totalCost,
            BigDecimal totalMargin,
            List<MissionLine> missions
    ) {
    }

    public record MissionLine(
            String missionNumber,
            Instant start,
            Instant end,
            String typeLabel,
            String origin,
            String destination,
            String driverName,
            BigDecimal distanceKm,
            BigDecimal revenue,
            BigDecimal cost,
            BigDecimal margin
    ) {
    }
}
