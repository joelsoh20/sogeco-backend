package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.DriverActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DriverActionRequest(

        @NotNull(message = "le type d'action est obligatoire")
        DriverActionType actionType,

        LocalDate actionDate,

        @NotBlank(message = "le motif est obligatoire")
        @Size(max = 255)
        String motif,

        @Size(max = 1000)
        String comment
) {
}
