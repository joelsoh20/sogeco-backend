package com.sogeco.fleet.modules.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Creation ou modification d'une mission")
public record MissionRequest(

        @Schema(description = "Obligatoire pour une livraison client")
        Long clientId,

        @NotNull(message = "le type de prestation est obligatoire")
        Long serviceTypeId,

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotNull(message = "le chauffeur est obligatoire")
        Long driverId,

        Long agencyId,

        @Schema(description = "Site d'arrivee, optionnel — utilise avec le site de depart pour une estimation de distance plus precise qu'une simple paire de villes")
        Long destinationAgencyId,

        Long originCityId,
        Long destinationCityId,

        @Schema(description = "Quartier ou marche de depart, optionnel — affine la distance estimee au-dela du seul site/centre-ville")
        Long originQuartierId,

        @Schema(description = "Quartier de livraison, optionnel — affine la distance estimee au-dela du seul centre-ville (voyage hors ville chez un client)")
        Long destinationQuartierId,

        @Size(max = 255)
        String departureAddress,

        @Size(max = 255)
        String destinationAddress,

        @Schema(description = "Date de depart planifiee, optionnelle — la date qui fait autorite reste actualStart, fixee au clic sur Demarrer")
        Instant plannedStart,

        Instant plannedArrival,

        @Size(max = 255)
        @Schema(example = "Marchandises diverses")
        String cargoDescription,

        @PositiveOrZero
        BigDecimal cargoWeightKg,

        @PositiveOrZero
        BigDecimal cargoVolumeM3,

        @Size(max = 60)
        @Schema(description = "Numero de bon de livraison du logiciel de stock")
        String externalReference,

        @PositiveOrZero
        @Schema(description = "Indemnite forfaitaire de voyage du chauffeur (nourriture, hebergement...) — surtout pertinent pour un voyage hors ville")
        BigDecimal missionFeeCost
) {
}
