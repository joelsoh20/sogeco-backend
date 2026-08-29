package com.sogeco.fleet.modules.quartier.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Creation ou modification d'un quartier")
public record QuartierRequest(

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100)
        @Schema(example = "Bonapriso")
        String name,

        @NotNull(message = "la ville est obligatoire")
        Long cityId,

        @Schema(description = "Couple colle depuis Google Maps — geocode automatiquement si absent", example = "4.0289, 9.7000")
        String coordinates
) {
}
