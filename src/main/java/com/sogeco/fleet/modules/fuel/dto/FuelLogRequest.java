package com.sogeco.fleet.modules.fuel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Saisie d'un plein")
public record FuelLogRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        Long driverId,

        @Schema(description = "Si absent, la mission en cours du camion est rattachee automatiquement")
        Long missionId,

        @Schema(description = "Station-service")
        Long partnerId,

        @NotNull(message = "la date et l'heure sont obligatoires")
        Instant fuelDatetime,

        @NotNull(message = "la quantite est obligatoire")
        @Positive(message = "la quantite doit etre positive")
        BigDecimal quantityLiters,

        @NotNull(message = "le prix au litre est obligatoire")
        @PositiveOrZero
        BigDecimal unitPrice,

        @Schema(description = "Si absent, le kilometrage courant du camion est utilise")
        @PositiveOrZero
        BigDecimal odometerBefore,

        @NotNull(message = "le kilometrage apres le plein est obligatoire")
        @PositiveOrZero
        BigDecimal odometerAfter,

        @Schema(description = "Indique si le reservoir a ete rempli a plein ; utilise pour le suivi du niveau de reservoir")
        Boolean fullTank,

        @Size(max = 50)
        String receiptNumber,

        @Schema(description = "Photo du compteur, televersee au prealable")
        Long receiptDocumentId
) {
}
