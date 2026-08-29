package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.UsageType;

/**
 * Ligne du classement "meilleur chauffeur" par ville et par type
 * d'usage (tour de ville / voyage) — nombre de livraisons et pannes
 * subies sur le semestre ecoule. usageType/vehicleId sont nuls quand
 * le chauffeur n'a actuellement aucun camion affecte.
 */
public record DriverSemesterRankingResponse(
        Long driverId,
        String driverName,
        Long cityId,
        String cityName,
        Long vehicleId,
        String registrationNumber,
        UsageType usageType,
        long deliveries,
        long breakdowns
) {
}
