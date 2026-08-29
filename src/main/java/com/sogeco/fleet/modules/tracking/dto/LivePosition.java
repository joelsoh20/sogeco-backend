package com.sogeco.fleet.modules.tracking.dto;

import com.sogeco.fleet.common.enums.VehicleStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Derniere position connue d'un camion, telle que servie a la carte.
 *
 * Enregistrement autonome, stocke en cache Redis : il ne depend
 * d'aucune entite JPA et se serialise directement.
 */
public record LivePosition(
        Long vehicleId,
        String registrationNumber,
        String deviceId,
        Long driverId,
        String driverName,
        Long missionId,
        String missionNumber,
        String destination,
        VehicleStatus status,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKmh,
        BigDecimal heading,
        Boolean ignitionOn,
        BigDecimal fuelLevelPercent,
        BigDecimal fuelLevelLiters,
        BigDecimal dailyKm,
        Instant recordedAt,
        /** Faux quand le camion n'a pas de boitier : position approchee depuis sa mission en cours. */
        boolean gpsTracked
) {

    /** Camion en mouvement au-dela du seuil d'arret parametre. */
    public boolean isMovingAbove(BigDecimal idleThreshold) {
        return speedKmh != null && speedKmh.compareTo(idleThreshold) > 0;
    }

    /** Camion sans trame depuis le delai parametre. */
    public boolean isOffline(int thresholdMinutes) {
        return recordedAt == null
                || recordedAt.isBefore(Instant.now().minusSeconds(thresholdMinutes * 60L));
    }
}
