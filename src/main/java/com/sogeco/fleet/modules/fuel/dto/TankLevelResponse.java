package com.sogeco.fleet.modules.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Niveau de carburant estime dans le reservoir d'un camion.
 *
 * Deux sources possibles : la telematique (boitier GPS/OBD, valeur
 * mesuree) prioritaire quand elle existe, sinon une estimation basee
 * sur le dernier plein complet et la distance parcourue depuis.
 */
public record TankLevelResponse(
        Long vehicleId,
        String registrationNumber,
        BigDecimal tankCapacityLiters,
        BigDecimal estimatedFuelLiters,
        BigDecimal estimatedFuelPercent,
        BigDecimal distanceSinceLastFillKm,
        Instant lastFullTankAt,
        TankLevelSource source
) {
    public enum TankLevelSource {
        TELEMATIQUE, ESTIMATION_DISTANCE, INDISPONIBLE
    }
}
