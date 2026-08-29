package com.sogeco.fleet.modules.client.dto;

import com.sogeco.fleet.common.enums.PricingMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Montant propose par la grille tarifaire, avant saisie")
public record TariffPreviewResponse(
        Long tariffId,
        PricingMode pricingMode,
        BigDecimal unitPrice,
        BigDecimal proposedAmount,
        String explanation,
        boolean found
) {
    public static TariffPreviewResponse notFound() {
        return new TariffPreviewResponse(null, null, null, null,
                "Aucun tarif applicable : le montant doit etre saisi manuellement", false);
    }
}
