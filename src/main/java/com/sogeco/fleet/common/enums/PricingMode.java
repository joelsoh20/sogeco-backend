package com.sogeco.fleet.common.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Mode de calcul d'un tarif. */
public enum PricingMode {

    /** Prix fixe, quels que soient la distance et le tonnage. */
    FORFAIT,

    /** Prix au kilometre parcouru. */
    PAR_KM,

    /** Prix a la tonne transportee. */
    PAR_TONNE;

    /**
     * Applique le tarif. Les grandeurs manquantes ramenent le calcul a
     * zero plutot que de produire un montant fantaisiste.
     */
    public BigDecimal apply(BigDecimal unitPrice, BigDecimal distanceKm, BigDecimal weightKg) {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return switch (this) {
            case FORFAIT -> unitPrice;
            case PAR_KM -> distanceKm == null
                    ? BigDecimal.ZERO
                    : unitPrice.multiply(distanceKm).setScale(2, RoundingMode.HALF_UP);
            case PAR_TONNE -> weightKg == null
                    ? BigDecimal.ZERO
                    : unitPrice.multiply(weightKg.divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP))
                               .setScale(2, RoundingMode.HALF_UP);
        };
    }
}
