package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.ClaimStatus;
import com.sogeco.fleet.common.enums.ClaimType;
import com.sogeco.fleet.modules.insurance.Claim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ClaimResponse(
        Long id,
        String claimNumber,
        Long vehicleId,
        String registrationNumber,
        Long driverId,
        String driverName,
        Long policyId,
        String policyNumber,
        LocalDate incidentDate,
        ClaimType claimType,
        String description,
        String locationLabel,
        String policeReportNumber,
        BigDecimal estimatedCost,
        BigDecimal deductibleAmount,
        BigDecimal reimbursedAmount,
        BigDecimal netCost,
        ClaimStatus status,
        Instant createdAt
) {
    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(
                c.getId(), c.getClaimNumber(),
                c.getVehicle().getId(), c.getVehicle().getRegistrationNumber(),
                c.getDriver() == null ? null : c.getDriver().getId(),
                c.getDriver() == null ? null : c.getDriver().getFullName(),
                c.getPolicy() == null ? null : c.getPolicy().getId(),
                c.getPolicy() == null ? null : c.getPolicy().getPolicyNumber(),
                c.getIncidentDate(), c.getClaimType(), c.getDescription(),
                c.getLocationLabel(), c.getPoliceReportNumber(),
                c.getEstimatedCost(), c.getDeductibleAmount(), c.getReimbursedAmount(),
                c.netCost(), c.getStatus(), c.getCreatedAt());
    }
}
