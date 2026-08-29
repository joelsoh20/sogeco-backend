package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.DriverStatus;
import com.sogeco.fleet.common.enums.RatingClass;
import com.sogeco.fleet.modules.driver.Driver;

import java.math.BigDecimal;

/** Ligne du classement des chauffeurs. */
public record DriverResponse(
        Long id,
        String matricule,
        String fullName,
        String phone,
        DriverStatus status,
        Long cityId,
        String cityName,
        Long vehicleId,
        String registrationNumber,
        Integer totalMissions,
        BigDecimal totalKilometers,
        Integer incidentsCount,
        BigDecimal performanceScore,
        RatingClass ratingClass,
        BigDecimal currentBonus,
        Long licenseDaysRemaining,
        Boolean active
) {
    public static DriverResponse from(Driver driver, Long vehicleId, String registrationNumber,
                                      BigDecimal currentBonus) {
        return new DriverResponse(
                driver.getId(),
                driver.getMatricule(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getStatus(),
                driver.getCity() == null ? null : driver.getCity().getId(),
                driver.getCity() == null ? null : driver.getCity().getName(),
                vehicleId,
                registrationNumber,
                driver.getTotalMissions(),
                driver.getTotalKilometers(),
                driver.getIncidentsCount(),
                driver.getPerformanceScore(),
                driver.getRatingClass(),
                currentBonus,
                driver.licenseDaysRemaining(),
                driver.getActive());
    }
}
