package com.sogeco.fleet.modules.mission.dto;

import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.modules.mission.Mission;

import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de la liste des missions. */
public record MissionResponse(
        Long id,
        String missionNumber,
        Instant plannedStart,
        String clientName,
        String originLabel,
        String destinationLabel,
        String registrationNumber,
        String driverName,
        MissionStatus status,
        BigDecimal progress,
        BigDecimal revenueAmount,
        BigDecimal totalCost,
        BigDecimal marginAmount,
        Boolean revenueMissing
) {
    public static MissionResponse from(Mission mission, boolean includeFinancials) {
        return new MissionResponse(
                mission.getId(),
                mission.getMissionNumber(),
                mission.getPlannedStart(),
                mission.getClient() == null ? null : mission.getClient().getCompanyName(),
                mission.originLabel(),
                mission.destinationLabel(),
                mission.getVehicle().getRegistrationNumber(),
                mission.getDriver().getFullName(),
                mission.getStatus(),
                mission.getProgress(),
                includeFinancials ? mission.getRevenueAmount() : null,
                includeFinancials ? mission.getTotalCost() : null,
                includeFinancials ? mission.getMarginAmount() : null,
                mission.isRevenueMissing());
    }
}
