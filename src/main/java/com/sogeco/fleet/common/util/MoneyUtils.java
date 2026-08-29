package com.sogeco.fleet.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Manipulation des montants en FCFA (XAF).
 *
 * Le franc CFA n'a pas de subdivision en circulation : les montants sont
 * stockes en NUMERIC(15,2) mais arrondis a l'unite a l'affichage.
 * Jamais de type Double pour un montant (CDC technique, anti-patterns).
 */
public final class MoneyUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MoneyUtils() {
    }

    public static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(nullSafe(value));
        }
        return total.setScale(SCALE, ROUNDING);
    }

    /** Division protegee contre le diviseur nul ou zero. */
    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        if (divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return nullSafe(dividend).divide(divisor, SCALE, ROUNDING);
    }

    /** Pourcentage de part : partie / total x 100. */
    public static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        return divide(nullSafe(part).multiply(HUNDRED), total);
    }

    /** Variation en pourcentage entre deux periodes (RG-2.2). */
    public static BigDecimal variation(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return divide(nullSafe(current).subtract(previous).multiply(HUNDRED), previous);
    }

    /** Arrondi a l'unite pour l'affichage en FCFA. */
    public static BigDecimal toDisplay(BigDecimal value) {
        return nullSafe(value).setScale(0, ROUNDING);
    }
}
