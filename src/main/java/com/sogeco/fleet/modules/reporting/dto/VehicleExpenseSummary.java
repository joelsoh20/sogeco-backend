package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Charges d'un camion pour une annee : missions terminees + entretien +
 * carburant hors mission (RG voir ReportingService.vehicleExpenses).
 * Pas de chiffre d'affaires ni de marge — un pur suivi de cout.
 */
public record VehicleExpenseSummary(
        Long vehicleId,
        String registrationNumber,
        Long cityId,
        String cityName,
        List<MonthlyAmount> monthly,
        BigDecimal yearTotal
) {
}
