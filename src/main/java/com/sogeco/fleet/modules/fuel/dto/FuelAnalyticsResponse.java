package com.sogeco.fleet.modules.fuel.dto;

import java.math.BigDecimal;
import java.util.List;

/** Indicateurs et repartitions de l'ecran Carburant. */
public record FuelAnalyticsResponse(
        BigDecimal totalCost,
        BigDecimal totalLiters,
        BigDecimal averageConsumption,
        BigDecimal costPerKm,
        BigDecimal averageUnitPrice,
        BigDecimal totalDistance,
        long anomalyCount,
        List<VehicleBreakdown> byVehicle,
        List<StationBreakdown> byStation
) {
    public record VehicleBreakdown(
            Long vehicleId, String registrationNumber,
            BigDecimal liters, BigDecimal cost, BigDecimal averageConsumption, BigDecimal share) {
    }

    public record StationBreakdown(
            Long stationId, String stationName, BigDecimal liters, BigDecimal cost) {
    }
}
