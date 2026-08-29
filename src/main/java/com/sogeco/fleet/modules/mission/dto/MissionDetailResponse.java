package com.sogeco.fleet.modules.mission.dto;

import com.sogeco.fleet.common.enums.CancellationReason;
import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.modules.document.dto.DocumentResponse;
import com.sogeco.fleet.modules.mission.Mission;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Panneau de detail de la fiche mission. */
public record MissionDetailResponse(
        Long id,
        String missionNumber,
        MissionStatus status,

        Long clientId,
        String clientName,
        String clientPhone,
        Long serviceTypeId,
        String serviceTypeLabel,
        Boolean billable,

        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        String driverPhone,
        Long agencyId,
        String agencyName,
        Long destinationAgencyId,
        String destinationAgencyName,

        Long routeId,
        String routeLabel,
        String departureLabel,
        String destinationLabel,
        BigDecimal distanceKm,
        BigDecimal referenceDistanceKm,

        Instant plannedStart,
        Instant plannedArrival,
        Instant actualStart,
        Instant actualEnd,
        Long durationMinutes,
        Boolean onTime,

        String cargoDescription,
        BigDecimal cargoWeightKg,
        BigDecimal cargoVolumeM3,
        BigDecimal fillRate,

        BigDecimal revenueAmount,
        String revenueNote,
        BigDecimal fuelCost,
        BigDecimal tollCost,
        BigDecimal driverCost,
        BigDecimal otherCost,
        BigDecimal missionFeeCost,
        BigDecimal totalCost,
        BigDecimal marginAmount,
        Boolean revenueMissing,

        BigDecimal progress,
        CancellationReason cancellationReason,
        String cancellationComment,
        String externalReference,
        List<WaypointResponse> waypoints,
        List<DocumentResponse> documents,
        Instant createdAt
) {
    public static MissionDetailResponse of(Mission m, Integer punctualityMargin,
                                           List<WaypointResponse> waypoints,
                                           List<DocumentResponse> documents,
                                           boolean includeFinancials) {
        return new MissionDetailResponse(
                m.getId(), m.getMissionNumber(), m.getStatus(),
                m.getClient() == null ? null : m.getClient().getId(),
                m.getClient() == null ? null : m.getClient().getCompanyName(),
                m.getClient() == null ? null : m.getClient().getPhone(),
                m.getServiceType().getId(), m.getServiceType().getLabel(), m.getServiceType().getBillable(),
                m.getVehicle().getId(), m.getVehicle().getRegistrationNumber(),
                m.getDriver().getId(), m.getDriver().getFullName(), m.getDriver().getPhone(),
                m.getAgency() == null ? null : m.getAgency().getId(),
                m.getAgency() == null ? null : m.getAgency().getName(),
                m.getDestinationAgency() == null ? null : m.getDestinationAgency().getId(),
                m.getDestinationAgency() == null ? null : m.getDestinationAgency().getName(),
                m.getRoute() == null ? null : m.getRoute().getId(),
                m.getRoute() == null ? null : m.getRoute().getLabel(),
                m.originLabel(),
                m.destinationLabel(),
                m.getDistanceKm(),
                m.getRoute() == null ? null : m.getRoute().getReferenceDistanceKm(),
                m.getPlannedStart(), m.getPlannedArrival(), m.getActualStart(), m.getActualEnd(),
                m.actualDurationMinutes(),
                punctualityMargin == null ? null : m.isOnTime(punctualityMargin),
                m.getCargoDescription(), m.getCargoWeightKg(), m.getCargoVolumeM3(), m.fillRate(),
                includeFinancials ? m.getRevenueAmount() : null,
                includeFinancials ? m.getRevenueNote() : null,
                includeFinancials ? m.getFuelCost() : null,
                includeFinancials ? m.getTollCost() : null,
                includeFinancials ? m.getDriverCost() : null,
                includeFinancials ? m.getOtherCost() : null,
                // Pas soumis a includeFinancials : saisi/modifie par qui cree la mission
                // (MISSION_UPDATE), pas seulement visible par la finance (FINANCE_READ).
                m.getMissionFeeCost(),
                includeFinancials ? m.getTotalCost() : null,
                includeFinancials ? m.getMarginAmount() : null,
                m.isRevenueMissing(),
                m.getProgress(), m.getCancellationReason(), m.getCancellationComment(),
                m.getExternalReference(), waypoints, documents, m.getCreatedAt());
    }
}
