package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.InspectionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TechnicalInspectionRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        Long partnerId,

        @NotNull(message = "la date de controle est obligatoire")
        LocalDate inspectionDate,

        @Schema(description = "Si absente, calculee depuis l'intervalle par defaut parametre")
        LocalDate nextInspectionDate,

        @NotNull(message = "le resultat est obligatoire")
        InspectionResult result,

        @Size(max = 500)
        String defectsNoted,

        @PositiveOrZero
        BigDecimal cost
) {
}
