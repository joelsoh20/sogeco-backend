package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.modules.insurance.CarteBleue;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteBleueResponse(
        Long id,
        Long vehicleId,
        String registrationNumber,
        String receiptNumber,
        String category,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysUntilExpiry,
        BigDecimal power,
        BigDecimal cost,
        String notes
) {
    public static CarteBleueResponse from(CarteBleue c) {
        return new CarteBleueResponse(
                c.getId(), c.getVehicle().getId(), c.getVehicle().getRegistrationNumber(),
                c.getReceiptNumber(), c.getCategory(), c.getIssueDate(), c.getExpiryDate(),
                c.daysUntilExpiry(), c.getPower(), c.getCost(), c.getNotes());
    }
}
