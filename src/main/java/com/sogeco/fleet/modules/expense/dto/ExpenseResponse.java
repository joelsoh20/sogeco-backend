package com.sogeco.fleet.modules.expense.dto;

import com.sogeco.fleet.common.enums.ExpenseCategory;
import com.sogeco.fleet.modules.expense.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseResponse(
        Long id, LocalDate expenseDate, ExpenseCategory category, String label, BigDecimal amount,
        Long vehicleId, String registrationNumber,
        Long driverId, String driverName,
        Long missionId, String missionNumber,
        Long agencyId, String agencyName,
        String notes) {

    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(), e.getExpenseDate(), e.getCategory(), e.getLabel(), e.getAmount(),
                e.getVehicle() == null ? null : e.getVehicle().getId(),
                e.getVehicle() == null ? null : e.getVehicle().getRegistrationNumber(),
                e.getDriver() == null ? null : e.getDriver().getId(),
                e.getDriver() == null ? null : e.getDriver().getFullName(),
                e.getMission() == null ? null : e.getMission().getId(),
                e.getMission() == null ? null : e.getMission().getMissionNumber(),
                e.getAgency() == null ? null : e.getAgency().getId(),
                e.getAgency() == null ? null : e.getAgency().getName(),
                e.getNotes());
    }
}
