package com.sogeco.fleet.modules.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Vue consolidee pour la Direction.
 *
 * Les classements haut/bas donnent en un coup d'oeil ce qui merite une
 * decision : les meilleurs camions confirment ce qui marche, les
 * moins bons sont les candidats a examiner en premier — vente,
 * reaffectation, ou simplement enquete sur pourquoi ils sous-performent.
 */
public record ExecutiveDashboardResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        FleetKpis kpis,
        List<VehicleMarginSummary> topVehicles,
        List<VehicleMarginSummary> bottomVehicles,
        List<ClientMarginSummary> topClients,
        long criticalAlertsCount,
        long missionsWithoutRevenue
) {
}
