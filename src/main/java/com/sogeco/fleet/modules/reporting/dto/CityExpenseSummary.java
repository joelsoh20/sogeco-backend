package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** Charges cumulees de tous les camions affectes a une ville, pour une annee. */
public record CityExpenseSummary(
        Long cityId,
        String cityName,
        int vehicleCount,
        List<MonthlyAmount> monthly,
        BigDecimal yearTotal
) {
}
