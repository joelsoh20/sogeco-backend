package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.ClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ClaimDecisionRequest(

        @NotNull(message = "le statut est obligatoire")
        ClaimStatus status,

        @PositiveOrZero
        BigDecimal reimbursedAmount
) {
}
