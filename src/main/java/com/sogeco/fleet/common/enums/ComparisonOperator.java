package com.sogeco.fleet.common.enums;

import java.math.BigDecimal;

/** Operateur de comparaison d'une regle d'alerte. */
public enum ComparisonOperator {
    GT, GTE, LT, LTE, EQ;

    public boolean test(BigDecimal value, BigDecimal threshold) {
        if (value == null || threshold == null) {
            return false;
        }
        int comparison = value.compareTo(threshold);
        return switch (this) {
            case GT  -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT  -> comparison < 0;
            case LTE -> comparison <= 0;
            case EQ  -> comparison == 0;
        };
    }
}
