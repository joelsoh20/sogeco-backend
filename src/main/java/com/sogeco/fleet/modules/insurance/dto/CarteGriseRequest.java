package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.BodyType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteGriseRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotBlank(message = "le numero d'immatriculation est obligatoire")
        @Size(max = 20)
        String registrationNumber,

        @NotBlank(message = "le numero de chassis est obligatoire")
        @Size(max = 30)
        String chassisNumber,

        @NotBlank(message = "la marque est obligatoire")
        @Size(max = 60)
        String brand,

        @Size(max = 30)
        @Schema(description = "Genre du vehicule tel qu'imprime sur la carte grise (VP, PL, CTTE...)")
        String genre,

        BodyType bodyType,

        @Positive
        Integer seatCount,

        LocalDate firstCirculationDate,

        @NotNull(message = "la date de delivrance est obligatoire")
        LocalDate issueDate,

        @Schema(description = "Si absente, calculee a la delivrance + duree de validite par defaut (10 ans)")
        LocalDate expiryDate,

        @PositiveOrZero
        BigDecimal cost,

        @Size(max = 500)
        String notes
) {
}
