package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.modules.insurance.CarteGrise;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CarteGriseResponse(
        Long id,
        Long vehicleId,
        String vehicleRegistrationNumber,
        String registrationNumber,
        String chassisNumber,
        String brand,
        String genre,
        BodyType bodyType,
        Integer seatCount,
        LocalDate firstCirculationDate,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysUntilExpiry,
        BigDecimal cost,
        String notes,
        Instant createdAt
) {
    public static CarteGriseResponse from(CarteGrise c) {
        return new CarteGriseResponse(
                c.getId(), c.getVehicle().getId(), c.getVehicle().getRegistrationNumber(),
                c.getRegistrationNumber(), c.getChassisNumber(), c.getBrand(), c.getGenre(),
                c.getBodyType(), c.getSeatCount(), c.getFirstCirculationDate(),
                c.getIssueDate(), c.getExpiryDate(), c.daysUntilExpiry(), c.getCost(), c.getNotes(),
                c.getCreatedAt());
    }
}
