package com.sogeco.fleet.modules.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FuelCancelRequest(
        @NotBlank(message = "le motif d'annulation est obligatoire")
        @Size(max = 255)
        String reason
) {
}
