package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

public record CorridorProfitability(
        Long routeId,
        String routeLabel,
        long missionCount,
        BigDecimal totalRevenue,
        BigDecimal totalCost,
        BigDecimal totalMargin,
        BigDecimal kmDriven,
        BigDecimal avgCostPerKm
) {
}
