package com.sogeco.fleet.modules.fuel.dto;

import com.sogeco.fleet.common.enums.FuelLogStatus;
import com.sogeco.fleet.modules.fuel.FuelLog;

import java.math.BigDecimal;
import java.time.Instant;

public record FuelLogResponse(
        Long id,
        Instant fuelDatetime,
        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        Long stationId,
        String stationName,
        Long missionId,
        String missionNumber,
        BigDecimal quantityLiters,
        BigDecimal unitPrice,
        BigDecimal totalCost,
        BigDecimal odometerBefore,
        BigDecimal odometerAfter,
        BigDecimal distanceCovered,
        Boolean fullTank,
        BigDecimal computedConsumption,
        FuelLogStatus status,
        String anomalyReason,
        String receiptNumber,
        Instant createdAt
) {
    public static FuelLogResponse from(FuelLog f) {
        return new FuelLogResponse(
                f.getId(), f.getFuelDatetime(),
                f.getVehicle().getId(), f.getVehicle().getRegistrationNumber(),
                f.getDriver() == null ? null : f.getDriver().getId(),
                f.getDriver() == null ? null : f.getDriver().getFullName(),
                f.getStation() == null ? null : f.getStation().getId(),
                f.getStation() == null ? null : f.getStation().getName(),
                f.getMission() == null ? null : f.getMission().getId(),
                f.getMission() == null ? null : f.getMission().getMissionNumber(),
                f.getQuantityLiters(), f.getUnitPrice(), f.getTotalCost(),
                f.getOdometerBefore(), f.getOdometerAfter(), f.distanceCovered(),
                f.getFullTank(), f.getComputedConsumption(),
                f.getStatus(), f.getAnomalyReason(), f.getReceiptNumber(),
                f.getCreatedAt());
    }
}
