package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.InspectionResult;
import com.sogeco.fleet.modules.insurance.TechnicalInspection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TechnicalInspectionResponse(
        Long id,
        Long vehicleId,
        String registrationNumber,
        Long centerId,
        String centerName,
        LocalDate inspectionDate,
        LocalDate nextInspectionDate,
        Long daysUntilNext,
        InspectionResult result,
        String defectsNoted,
        BigDecimal cost
) {
    public static TechnicalInspectionResponse from(TechnicalInspection i) {
        return new TechnicalInspectionResponse(
                i.getId(), i.getVehicle().getId(), i.getVehicle().getRegistrationNumber(),
                i.getCenter() == null ? null : i.getCenter().getId(),
                i.getCenter() == null ? null : i.getCenter().getName(),
                i.getInspectionDate(), i.getNextInspectionDate(), i.daysUntilNext(),
                i.getResult(), i.getDefectsNoted(), i.getCost());
    }
}
