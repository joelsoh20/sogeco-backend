package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

public record AgencyProfitability(
        Long agencyId,
        String agencyName,
        long missionCount,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin
) {
}
