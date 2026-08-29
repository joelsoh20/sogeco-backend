package com.sogeco.fleet.modules.alert.dto;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.Alert;

import java.math.BigDecimal;
import java.time.Instant;

public record AlertResponse(
        Long id,
        AlertType alertType,
        AlertLevel level,
        String title,
        String description,
        Instant triggeredAt,
        Long ageMinutes,
        AlertStatus status,
        Integer occurrences,
        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        String driverPhone,
        Long missionId,
        BigDecimal latitude,
        BigDecimal longitude,
        String locationLabel,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Long resolutionMinutes,
        String resolutionNote
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(
                a.getId(), a.getAlertType(), a.getLevel(), a.getTitle(), a.getDescription(),
                a.getTriggeredAt(), a.ageMinutes(), a.getStatus(), a.getOccurrences(),
                a.getVehicle() == null ? null : a.getVehicle().getId(),
                a.getVehicle() == null ? null : a.getVehicle().getRegistrationNumber(),
                a.getDriver() == null ? null : a.getDriver().getId(),
                a.getDriver() == null ? null : a.getDriver().getFullName(),
                // Le telephone permet le bouton "Contacter le chauffeur"
                a.getDriver() == null ? null : a.getDriver().getPhone(),
                a.getMission() == null ? null : a.getMission().getId(),
                a.getLatitude(), a.getLongitude(), a.getLocationLabel(),
                a.getAcknowledgedAt(), a.getResolvedAt(), a.resolutionMinutes(),
                a.getResolutionNote());
    }
}
