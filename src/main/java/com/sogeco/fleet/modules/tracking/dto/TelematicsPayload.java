package com.sogeco.fleet.modules.tracking.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Trame normalisee, independante du fournisseur.
 *
 * Chaque adaptateur traduit le format vendeur vers ce modele : ajouter
 * un prestataire ne touche donc a aucun code d'ingestion, d'alerte ou
 * de diffusion.
 *
 * Tous les champs hors position sont facultatifs : un boitier d'entree
 * de gamme ne remonte ni carburant, ni temperature, ni code defaut.
 */
@Builder
public record TelematicsPayload(

        String deviceId,
        Instant recordedAt,

        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal speedKmh,
        BigDecimal heading,
        BigDecimal altitude,

        Boolean ignitionOn,
        BigDecimal odometerKm,
        BigDecimal fuelLevelPercent,
        BigDecimal fuelLevelLiters,

        BigDecimal engineTemperature,
        Integer engineRpm,
        BigDecimal batteryVoltage,
        BigDecimal engineHours,
        List<String> errorCodes,

        String protocol,
        Boolean valid,
        String alarm
) {

    public boolean hasDiagnostics() {
        return engineTemperature != null || engineRpm != null
                || batteryVoltage != null || (errorCodes != null && !errorCodes.isEmpty());
    }

    public boolean hasFault() {
        return errorCodes != null && !errorCodes.isEmpty();
    }

    public boolean isValidPosition() {
        return latitude != null && longitude != null
                && Boolean.TRUE.equals(valid == null ? Boolean.TRUE : valid);
    }
}
