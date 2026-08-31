package com.sogeco.fleet.modules.vehicle.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.document.dto.DocumentResponse;
import com.sogeco.fleet.modules.vehicle.Vehicle;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Panneau de detail de la fiche camion, avec bloc Documents et Echeances. */
public record VehicleDetailResponse(
        Long id,
        String registrationNumber,
        String vinNumber,
        String brand,
        String model,
        BodyType bodyType,
        BigDecimal capacityTons,
        BigDecimal tankCapacityLiters,
        BigDecimal grossWeightKg,
        LocalDate firstRegistrationDate,
        String ownerName,
        LocalDate purchaseDate,
        BigDecimal purchasePrice,
        VehicleStatus status,
        Long cityId,
        String cityName,
        String deviceId,
        BigDecimal currentKilometers,
        BigDecimal dailyKm,
        BigDecimal fuelLevelPercent,
        BigDecimal fuelLevelLiters,
        BigDecimal avgFuelConsumption,
        BigDecimal avgFuelConsumptionLoaded,
        LocalDate nextMaintenanceDate,
        BigDecimal nextMaintenanceKm,
        Long driverId,
        String driverName,
        String driverPhone,
        Boolean assignable,
        List<String> blockingReasons,
        List<DocumentResponse> documents,
        Boolean active,
        Instant createdAt,
        UsageType usageType,
        BigDecimal weeklyWashCost
) {
    public static VehicleDetailResponse of(Vehicle vehicle,
                                           Long driverId, String driverName, String driverPhone,
                                           boolean assignable,
                                           List<String> blockingReasons,
                                           List<DocumentResponse> documents) {
        return new VehicleDetailResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getVinNumber(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getBodyType(),
                vehicle.getCapacityTons(),
                vehicle.getTankCapacityLiters(),
                vehicle.getGrossWeightKg(),
                vehicle.getFirstRegistrationDate(),
                vehicle.getOwnerName(),
                vehicle.getPurchaseDate(),
                vehicle.getPurchasePrice(),
                vehicle.getStatus(),
                vehicle.getCity() == null ? null : vehicle.getCity().getId(),
                vehicle.getCity() == null ? null : vehicle.getCity().getName(),
                vehicle.getDeviceId(),
                vehicle.getCurrentKilometers(),
                vehicle.getDailyKm(),
                vehicle.getFuelLevelPercent(),
                vehicle.getFuelLevelLiters(),
                vehicle.getAvgFuelConsumption(),
                vehicle.getAvgFuelConsumptionLoaded(),
                vehicle.getNextMaintenanceDate(),
                vehicle.getNextMaintenanceKm(),
                driverId, driverName, driverPhone,
                assignable, blockingReasons, documents,
                vehicle.getActive(), vehicle.getCreatedAt(),
                vehicle.getUsageType(), vehicle.getWeeklyWashCost());
    }
}
