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

        @Schema(description = "Somme depensee pour le plein (FCFA) : on carbure en fonction du budget. "
                + "Les litres en sont deduits (somme / prix au litre).")
        @NotNull(message = "la somme consommee est obligatoire")
        @Positive(message = "la somme consommee doit etre positive")
        BigDecimal totalCost,

        @NotNull(message = "le prix au litre est obligatoire")
        @Positive(message = "le prix au litre doit etre positif")
        BigDecimal unitPrice,

        @Schema(description = "Indique si le reservoir a ete rempli a plein ; utilise pour le suivi du niveau de reservoir")
        Boolean fullTank,

        @Size(max = 50)
        String receiptNumber,

        @Schema(description = "Photo du compteur, televersee au prealable")
        Long receiptDocumentId
) {
}
