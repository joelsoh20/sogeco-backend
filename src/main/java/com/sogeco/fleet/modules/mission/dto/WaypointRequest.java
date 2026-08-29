package com.sogeco.fleet.modules.mission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record WaypointRequest(

        @NotNull(message = "le rang est obligatoire")
        @Positive
        Integer sequenceNumber,

        @NotBlank(message = "le libelle est obligatoire")
        @Size(max = 150)
        String label,

        @Size(max = 255)
        String address,

        String coordinates,
        Instant plannedArrival,

        @Size(max = 255)
        String notes
) {
}
