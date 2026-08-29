package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.BonusStatus;
import com.sogeco.fleet.modules.driver.DriverBonus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BonusResponse(
        Long id,
        Long driverId,
        String driverName,
        LocalDate periodMonth,
        BigDecimal amount,
        BigDecimal performanceScore,
        String reason,
        BonusStatus status,
        Instant grantedAt,
        Instant paidAt
) {
    public static BonusResponse from(DriverBonus bonus) {
        return new BonusResponse(
                bonus.getId(),
                bonus.getDriver().getId(),
                bonus.getDriver().getFullName(),
                bonus.getPeriodMonth(),
                bonus.getAmount(),
                bonus.getPerformanceScore(),
                bonus.getReason(),
                bonus.getStatus(),
                bonus.getGrantedAt(),
                bonus.getPaidAt());
    }
}
