package com.sogeco.fleet.modules.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AssignmentRequest(

        @NotNull(message = "le chauffeur est obligatoire")
        Long driverId,

        LocalDate startDate,

        @Size(max = 255)
        String notes
) {
}
