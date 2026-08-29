package com.sogeco.fleet.modules.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Corridor inter-agences et ses valeurs de reference")
public record RouteRequest(

        @NotNull(message = "la ville de depart est obligatoire")
        Long originCityId,

        @NotNull(message = "la ville d'arrivee est obligatoire")
        Long destinationCityId,

        @Positive(message = "la distance doit etre positive")
        @Schema(description = "RELEVEE sur les trajets reels, pas estimee par un calculateur routier")
        BigDecimal referenceDistanceKm,

        @Positive
        Integer referenceDurationMinutes,

        @Positive
        @Schema(description = "Consommation attendue sur le corridor, en litres")
        BigDecimal referenceFuelLiters,

        @PositiveOrZero
        @Schema(description = "Largeur du couloir admis avant alerte de deviation (km)")
        BigDecimal toleranceKm,

        @Schema(description = "Trace de l'axe au format GeoJSON")
        String corridorGeojson
) {
}
