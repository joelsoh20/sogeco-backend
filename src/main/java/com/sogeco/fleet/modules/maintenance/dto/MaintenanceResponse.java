package com.sogeco.fleet.modules.maintenance.dto;

import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceItemType;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.modules.maintenance.MaintenanceItem;
import com.sogeco.fleet.modules.maintenance.MaintenanceLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MaintenanceResponse(
        Long id,
        Long vehicleId,
        String registrationNumber,
        Long garageId,
        String garageName,
        MaintenanceCategory category,
        String description,
        LocalDate interventionDate,
        LocalDate completionDate,
        BigDecimal odometerKm,
        BigDecimal partsCost,
        BigDecimal laborCost,
        BigDecimal totalCost,
        MaintenanceStatus status,
        Integer downtimeDays,
        Boolean isBreakdown,
        Boolean isRecurrence,
        String errorCode,
        LocalDate nextInterventionDate,
        BigDecimal nextInterventionKm,
        Long daysUntilNext,
        List<ItemLine> items,
        Instant createdAt
) {
    public record ItemLine(Long id, MaintenanceItemType itemType, String label,
                           BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {

        static ItemLine from(MaintenanceItem item) {
            return new ItemLine(item.getId(), item.getItemType(), item.getLabel(),
                    item.getQuantity(), item.getUnitPrice(),
                    item.getTotal() == null ? item.computeTotal() : item.getTotal());
        }
    }

    public static MaintenanceResponse from(MaintenanceLog m, boolean withItems) {
        return new MaintenanceResponse(
                m.getId(),
                m.getVehicle().getId(), m.getVehicle().getRegistrationNumber(),
                m.getGarage() == null ? null : m.getGarage().getId(),
                m.getGarage() == null ? null : m.getGarage().getName(),
                m.getCategory(), m.getDescription(),
                m.getInterventionDate(), m.getCompletionDate(), m.getOdometerKm(),
                m.getPartsCost(), m.getLaborCost(), m.getTotalCost(),
                m.getStatus(), m.getDowntimeDays(), m.getIsBreakdown(), m.getIsRecurrence(),
                m.getErrorCode(), m.getNextInterventionDate(), m.getNextInterventionKm(),
                m.daysUntilNext(),
                withItems ? m.getItems().stream().map(ItemLine::from).toList() : List.of(),
                m.getCreatedAt());
    }
}
