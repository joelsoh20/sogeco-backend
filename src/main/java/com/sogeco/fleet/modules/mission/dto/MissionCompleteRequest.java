package com.sogeco.fleet.modules.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Cloture de mission : le suivi porte sur les charges par camion, le chiffre d'affaires est facultatif")
public record MissionCompleteRequest(

        @PositiveOrZero(message = "le montant ne peut pas etre negatif")
        @Schema(description = "Facultatif — absent ou nul si non facture")
        BigDecimal revenueAmount,

        @PositiveOrZero
        @Schema(description = "Distance reelle. Si absente, celle mesuree par le GPS est conservee.")
        BigDecimal distanceKm,

        @PositiveOrZero
        BigDecimal tollCost,

        @PositiveOrZero
        BigDecimal otherCost,

        @Size(max = 255)
        @Schema(description = "Justification si le montant differe de la proposition tarifaire")
        String revenueNote
) {
}
