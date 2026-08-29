package com.sogeco.fleet.modules.vehicle.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.UsageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Fiche camion")
public record VehicleRequest(

        @NotBlank(message = "l'immatriculation est obligatoire")
        @Size(max = 20)
        @Schema(example = "LT-236-AA")
        String registrationNumber,

        @Size(max = 30)
        @Schema(description = "Numero de chassis, figure sur la carte grise")
        String vinNumber,

        @NotBlank(message = "la marque est obligatoire")
        @Size(max = 60)
        @Schema(example = "Mercedes")
        String brand,

        @NotBlank(message = "le modele est obligatoire")
        @Size(max = 60)
        @Schema(example = "Actros 1845")
        String model,

        @NotNull(message = "le type de carrosserie est obligatoire")
        BodyType bodyType,

        @Positive(message = "la capacite doit etre positive")
        @Schema(example = "30")
        BigDecimal capacityTons,

        @Positive(message = "la capacite du reservoir doit etre positive")
        @Schema(example = "400")
        BigDecimal tankCapacityLiters,

        @Positive
        BigDecimal grossWeightKg,

        LocalDate firstRegistrationDate,

        @Size(max = 120)
        String ownerName,

        LocalDate purchaseDate,

        @PositiveOrZero
        BigDecimal purchasePrice,

        Long cityId,

        @Size(max = 60)
        @Schema(description = "Identifiant du boitier telematique")
        String deviceId,

        @PositiveOrZero(message = "le kilometrage ne peut pas etre negatif")
        BigDecimal currentKilometers,

        @NotNull(message = "le type d'usage est obligatoire")
        @Schema(description = "Voyage (longue distance) ou tour de ville (rotations locales)")
        UsageType usageType,

        @Schema(description = "Frais de lavage hebdomadaire — uniquement pour un camion en tour de ville, 2500 ou 3000 FCFA",
                example = "2500")
        BigDecimal weeklyWashCost
) {
}
