package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.modules.insurance.TransportLicense;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransportLicenseResponse(
        Long id,
        String reference,
        String issuingAuthority,
        String receiptNumber,
        String power,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysRemaining,
        PolicyStatus status,
        BigDecimal cost,
        String notes
) {
    public static TransportLicenseResponse from(TransportLicense l) {
        return new TransportLicenseResponse(
                l.getId(), l.getReference(), l.getIssuingAuthority(),
                l.getReceiptNumber(), l.getPower(),
                l.getIssueDate(), l.getExpiryDate(), l.daysRemaining(),
                l.getStatus(), l.getCost(), l.getNotes());
    }
}
