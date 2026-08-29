package com.sogeco.fleet.modules.reporting.dto;

import com.sogeco.fleet.modules.insurance.dto.DeadlineItem;

import java.util.List;

/** Vue du jour pour le pilotage operationnel. */
public record OperationalDashboardResponse(
        long missionsToday,
        long missionsInProgress,
        long missionsPending,
        long vehiclesInMaintenance,
        long vehiclesInBreakdown,
        long unassignedDrivers,
        long openAlerts,
        long criticalAlerts,
        List<DeadlineItem> upcomingDeadlines
) {
}
