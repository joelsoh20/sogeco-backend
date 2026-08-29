package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.BonusStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Validation, refus ou ajustement d'une prime")
public record BonusDecisionRequest(

        @NotNull(message = "le statut est obligatoire")
        BonusStatus status,

        @PositiveOrZero
        @Schema(description = "Montant ajuste. Si absent, le montant propose est conserve.")
        BigDecimal amount,

        @Size(max = 255)
        @Schema(description = "Obligatoire si le montant differe de la proposition")
        String reason
) {
}
