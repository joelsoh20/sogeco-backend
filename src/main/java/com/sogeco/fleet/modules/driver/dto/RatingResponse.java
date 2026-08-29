package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.RatingCriterion;
import com.sogeco.fleet.modules.driver.DriverRating;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RatingResponse(
        Long id,
        LocalDate periodMonth,
        RatingCriterion criterion,
        BigDecimal score100,
        Boolean isAutomatic,
        String comment
) {
    public static RatingResponse from(DriverRating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getPeriodMonth(),
                rating.getCriterion(),
                rating.getScore100(),
                rating.getIsAutomatic(),
                rating.getComment());
    }
}
