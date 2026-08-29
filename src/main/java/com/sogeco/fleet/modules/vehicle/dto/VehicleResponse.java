package com.sogeco.fleet.modules.vehicle.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.vehicle.Vehicle;

import java.math.BigDecimal;

/** Ligne de la liste des camions. */
public record VehicleResponse(
        Long id,
        String registrationNumber,
        String brand,
        String model,
        BodyType bodyType,
        BigDecimal capacityTons,
        BigDecimal tankCapacityLiters,
        VehicleStatus status,
        Long cityId,
        String cityName,
        Long driverId,
        String driverName,
        BigDecimal currentKilometers,
        BigDecimal dailyKm,
        BigDecimal avgFuelConsumption,
        BigDecimal fuelLevelPercent,
        Boolean active,
        UsageType usageType,
        BigDecimal weeklyWashCost
) {
    public static VehicleResponse from(Vehicle vehicle, Long driverId, String driverName) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getBodyType(),
                vehicle.getCapacityTons(),
                vehicle.getTankCapacityLiters(),
                vehicle.getStatus(),
                vehicle.getCity() == null ? null : vehicle.getCity().getId(),
                vehicle.getCity() == null ? null : vehicle.getCity().getName(),
                driverId,
                driverName,
                vehicle.getCurrentKilometers(),
                vehicle.getDailyKm(),
                vehicle.getAvgFuelConsumption(),
                vehicle.getFuelLevelPercent(),
                vehicle.getActive(),
                vehicle.getUsageType(),
                vehicle.getWeeklyWashCost());
    }
}
