package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.DriverActionType;
import com.sogeco.fleet.modules.driver.DriverAction;

import java.time.LocalDate;

public record DriverActionResponse(
        Long id,
        Long driverId,
        DriverActionType actionType,
        LocalDate actionDate,
        String motif,
        String comment,
        String createdBy
) {
    public static DriverActionResponse from(DriverAction action) {
        return new DriverActionResponse(
                action.getId(),
                action.getDriver().getId(),
                action.getActionType(),
                action.getActionDate(),
                action.getMotif(),
                action.getComment(),
                action.getCreatedBy());
    }
}
