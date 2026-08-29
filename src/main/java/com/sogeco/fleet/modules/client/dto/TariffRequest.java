package com.sogeco.fleet.modules.client.dto;

import com.sogeco.fleet.common.enums.PricingMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Ligne de grille tarifaire")
public record TariffRequest(

        @Schema(description = "Nul pour un tarif general applicable a defaut de tarif negocie")
        Long clientId,

        @NotNull(message = "le type de prestation est obligatoire")
        Long serviceTypeId,

        @Schema(description = "Nul pour un tarif valable quel que soit le corridor")
        Long routeId,

        @NotNull(message = "le mode de tarification est obligatoire")
        PricingMode pricingMode,

        @NotNull(message = "le prix unitaire est obligatoire")
        @PositiveOrZero
        @Schema(description = "Forfait, prix au km, ou prix a la tonne selon le mode")
        BigDecimal unitPrice,

        @PositiveOrZero
        @Schema(description = "Montant plancher applique si le calcul donne moins")
        BigDecimal minAmount,

        @NotNull(message = "la date de debut de validite est obligatoire")
        LocalDate validFrom,

        LocalDate validTo
) {
}
