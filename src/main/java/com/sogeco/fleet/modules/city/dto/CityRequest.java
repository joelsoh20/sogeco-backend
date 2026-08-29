package com.sogeco.fleet.modules.city.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Creation ou modification d'une ville")
public record CityRequest(

        @Size(max = 10, message = "le code ne peut depasser 10 caracteres")
        @Schema(description = "Optionnel — genere automatiquement a partir du nom si absent", example = "DLA")
        String code,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100)
        @Schema(example = "Douala")
        String name,

        @Size(max = 100)
        @Schema(example = "Littoral")
        String region,

        @Schema(description = "Couple colle depuis Google Maps", example = "4.0511, 9.7679")
        String coordinates,

        @Schema(description = "Vrai si SOGECO y possede une implantation")
        Boolean hasSite
) {
}
