package com.sogeco.fleet.modules.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Modele de livraison quotidienne recurrente")
public record MissionAutomationRequest(

        @Size(max = 150)
        @Schema(description = "Nom libre, pour se reperer dans la liste des automatisations")
        String label,

        @NotNull(message = "la ville est obligatoire")
        Long cityId,

        @NotNull(message = "le type de prestation est obligatoire")
        Long serviceTypeId,

        Long clientId,

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotNull(message = "le chauffeur est obligatoire")
        Long driverId,

        @NotNull(message = "le site de depart est obligatoire")
        Long agencyId,

        @NotNull(message = "le point de livraison est obligatoire")
        Long destinationQuartierId,

        @Size(max = 255)
        String cargoDescription
) {
}
