package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

public record ClientProfitability(
        Long clientId,
        String clientName,
        long missionCount,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin,
        BigDecimal marginPercent
) {
}
