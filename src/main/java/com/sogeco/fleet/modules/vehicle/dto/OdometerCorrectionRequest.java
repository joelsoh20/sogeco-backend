package com.sogeco.fleet.modules.vehicle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

@Schema(description = "Correction administrative du kilometrage, tracee au journal d'audit")
public record OdometerCorrectionRequest(

        @NotNull(message = "la valeur est obligatoire")
        @PositiveOrZero
        BigDecimal kilometers,

        @NotBlank(message = "le motif est obligatoire")
        String motif
) {
}
