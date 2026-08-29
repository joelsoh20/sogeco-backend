package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.InsuranceCoverageType;
import com.sogeco.fleet.common.enums.PaymentFrequency;
import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.modules.insurance.InsurancePolicy;
import com.sogeco.fleet.modules.vehicle.Vehicle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record InsurancePolicyResponse(
        Long id,
        String policyNumber,
        Long insurerId,
        String insurerName,
        InsuranceCoverageType coverageType,
        BodyType category,
        String vehicleRegistration,
        BigDecimal premiumAmount,
        PaymentFrequency paymentFrequency,
        LocalDate startDate,
        LocalDate endDate,
        Long daysRemaining,
        PolicyStatus status,
        List<String> vehicles,
        Boolean coversFleet,
        String notes
) {
    public static InsurancePolicyResponse from(InsurancePolicy p) {
        return new InsurancePolicyResponse(
                p.getId(), p.getPolicyNumber(),
                p.getInsurer().getId(), p.getInsurer().getName(),
                p.getCoverageType(), p.getCategory(), p.getVehicleRegistration(),
                p.getPremiumAmount(), p.getPaymentFrequency(),
                p.getStartDate(), p.getEndDate(), p.daysRemaining(), p.getStatus(),
                p.getVehicles().stream().map(Vehicle::getRegistrationNumber).sorted().toList(),
                p.coversFleet(), p.getNotes());
    }
}
