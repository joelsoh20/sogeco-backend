package com.sogeco.fleet.modules.alert.dto;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.ComparisonOperator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AlertRuleRequest(
        BigDecimal thresholdValue,
        ComparisonOperator comparisonOperator,

        @NotNull(message = "le niveau est obligatoire")
        AlertLevel level,

        @Positive(message = "le delai anti-repetition doit etre positif")
        Integer cooldownMinutes,

        @Size(max = 255)
        String notifyRoleCodes,

        @NotNull(message = "l'etat actif est obligatoire")
        Boolean active
) {
}
