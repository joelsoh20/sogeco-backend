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
        return from(driver, vehicleId, registrationNumber, currentBonus, driver.getTotalKilometers());
    }

    /**
     * Variante avec un kilometrage impose par l'appelant -- typiquement
     * le kilometrage GPS reel (DriverService.gpsKilometers()), plus
     * fiable que le compteur driver.totalKilometers qui ne bouge qu'a
     * la cloture d'une mission dans l'appli.
     */
    public static DriverResponse from(Driver driver, Long vehicleId, String registrationNumber,
                                      BigDecimal currentBonus, BigDecimal totalKilometers) {
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
                totalKilometers,
                driver.getIncidentsCount(),
                driver.getPerformanceScore(),
                driver.getRatingClass(),
                currentBonus,
                driver.licenseDaysRemaining(),
                driver.getActive());
    }
}
