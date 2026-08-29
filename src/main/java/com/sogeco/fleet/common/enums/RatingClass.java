package com.sogeco.fleet.common.enums;

import java.math.BigDecimal;

/** Classes de notation affichees sur l'ecran Chauffeurs (RG-9.10). */
public enum RatingClass {
    EXCELLENT,
    BON,
    MOYEN,
    FAIBLE;

    public static RatingClass of(BigDecimal score) {
        if (score == null) {
            return FAIBLE;
        }
        int value = score.intValue();
        if (value >= 90) return EXCELLENT;
        if (value >= 70) return BON;
        if (value >= 50) return MOYEN;
        return FAIBLE;
    }
}
