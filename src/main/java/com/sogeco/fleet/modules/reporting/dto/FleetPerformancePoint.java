package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Point mensuel de la courbe "Performance de la flotte" de l'ecran
 * d'accueil — cout carburant et cout maintenance, restreints aux
 * camions des villes d'implantation actives (voir
 * ReportingService.ACTIVE_CITY_NAMES).
 */
public record FleetPerformancePoint(
        LocalDate month,
        BigDecimal fuelCost,
        BigDecimal maintenanceCost
) {
}
