package com.sogeco.fleet.modules.vehicle.dto;

import com.sogeco.fleet.modules.vehicle.VehicleAssignment;

import java.time.LocalDate;

public record AssignmentResponse(
        Long id,
        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        LocalDate startDate,
        LocalDate endDate,
        boolean active,
        String notes
) {
    public static AssignmentResponse from(VehicleAssignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getVehicle().getId(),
                assignment.getVehicle().getRegistrationNumber(),
                assignment.getDriver().getId(),
                assignment.getDriver().getFullName(),
                assignment.getStartDate(),
                assignment.getEndDate(),
                assignment.isActive(),
                assignment.getNotes());
    }
}
