package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.ClaimType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Declaration de sinistre")
public record ClaimRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        Long driverId,

        @Schema(description = "Facultatif : un accrochage mineur peut ne jamais etre declare a l'assureur")
        Long insurancePolicyId,

        @NotNull(message = "la date du sinistre est obligatoire")
        LocalDate incidentDate,

        @NotNull(message = "le type de sinistre est obligatoire")
        ClaimType claimType,

        @NotBlank(message = "la description est obligatoire")
        @Size(max = 1000)
        String description,

        @Size(max = 255)
        String locationLabel,

        @Size(max = 60)
        String policeReportNumber,

        @PositiveOrZero
        BigDecimal estimatedCost,

        @PositiveOrZero
        BigDecimal deductibleAmount
) {
}
