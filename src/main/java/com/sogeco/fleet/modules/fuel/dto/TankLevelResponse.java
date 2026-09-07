package com.sogeco.fleet.modules.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Niveau de carburant estime dans le reservoir d'un camion.
 *
 * Trois sources possibles, par ordre de fiabilite decroissante : la
 * telematique (boitier GPS/OBD, valeur mesuree) ; a defaut, une
 * estimation basee sur le dernier plein connu de CE camion et la
 * distance qu'il a parcourue depuis (ESTIMATION_DISTANCE) ; a defaut
 * encore (pas assez d'historique propre au camion pour connaitre son
 * taux de consommation), une estimation basee sur la consommation
 * moyenne des camions de meme carrosserie (ESTIMATION_APPROXIMATIVE) --
 * moins fiable, mais un chiffre approximatif reste plus utile qu'aucun
 * chiffre pour un camion sans jauge physique.
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
        TELEMATIQUE, ESTIMATION_DISTANCE, ESTIMATION_APPROXIMATIVE, INDISPONIBLE
    }
}
