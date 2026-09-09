package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.modules.insurance.CarteRose;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CarteRoseResponse(
        Long id,
        Long vehicleId,
        String vehicleRegistrationNumber,
        Long insurerId,
        String insurerName,
        String insuredName,
        String insuredAddress,
        String issuingBureauName,
        String issuingBureauAddress,
        String registrationNumber,
        String vehicleMakeType,
        String vehicleCategory,
        LocalDate validFrom,
        LocalDate validTo,
        Long daysUntilExpiry,
        BigDecimal cost,
        String notes,
        Instant createdAt
) {
    public static CarteRoseResponse from(CarteRose c) {
        return new CarteRoseResponse(
                c.getId(), c.getVehicle().getId(), c.getVehicle().getRegistrationNumber(),
                c.getInsurer().getId(), c.getInsurer().getName(),
                c.getInsuredName(), c.getInsuredAddress(),
                c.getIssuingBureauName(), c.getIssuingBureauAddress(),
                c.getRegistrationNumber(), c.getVehicleMakeType(), c.getVehicleCategory(),
                c.getValidFrom(), c.getValidTo(), c.daysUntilExpiry(),
                c.getCost(), c.getNotes(), c.getCreatedAt());
    }
}
