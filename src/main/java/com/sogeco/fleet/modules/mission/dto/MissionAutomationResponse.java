package com.sogeco.fleet.modules.mission.dto;

import com.sogeco.fleet.modules.mission.MissionAutomation;

/** Ligne de la liste des livraisons automatisees. */
public record MissionAutomationResponse(
        Long id,
        String label,
        Long cityId,
        String cityName,
        String serviceTypeLabel,
        String clientName,
        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        String agencyName,
        String destinationQuartierName,
        String cargoDescription,
        Boolean active
) {
    public static MissionAutomationResponse from(MissionAutomation a) {
        return new MissionAutomationResponse(
                a.getId(),
                a.getLabel(),
                a.getCity().getId(),
                a.getCity().getName(),
                a.getServiceType().getLabel(),
                a.getClient() == null ? null : a.getClient().getCompanyName(),
                a.getVehicle().getId(),
                a.getVehicle().getRegistrationNumber(),
                a.getDriver().getId(),
                a.getDriver().getFullName(),
                a.getAgency().getName(),
                a.getDestinationQuartier().getName(),
                a.getCargoDescription(),
                a.getActive());
    }
}
