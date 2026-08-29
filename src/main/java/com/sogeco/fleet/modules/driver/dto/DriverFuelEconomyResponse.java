package com.sogeco.fleet.modules.driver.dto;

import java.math.BigDecimal;

/** Une ligne du classement "Top 5 economie de carburant". */
public record DriverFuelEconomyResponse(
        Long driverId,
        String driverName,
        BigDecimal averageConsumption,
        BigDecimal totalLiters,
        BigDecimal totalCost
) {
}
