package com.sogeco.fleet.modules.fuel.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.modules.fuel.dto.TankLevelResponse.TankLevelSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Kilometrage, consommation et carburant a ajouter pour la periode — "
        + "pense pour les vehicules a suivi allege (moto, tricycle, voiture de livraison), "
        + "carbures en general chaque samedi")
public record WeeklyRefuelResponse(
        Long vehicleId,
        String registrationNumber,
        BodyType bodyType,

        @Schema(description = "Kilometres parcourus sur la periode")
        BigDecimal distanceKm,

        @Schema(description = "Consommation moyenne du vehicule (L/100km), null si pas encore de plein")
        BigDecimal avgConsumptionPer100km,

        BigDecimal tankCapacityLiters,

        @Schema(description = "Niveau de carburant estime dans le reservoir, avant le plein")
        BigDecimal estimatedFuelLiters,

        @Schema(description = "Quantite a ajouter pour faire le plein — null si le niveau ne peut etre estime")
        BigDecimal suggestedRefillLiters,

        TankLevelSource source
) {
}
