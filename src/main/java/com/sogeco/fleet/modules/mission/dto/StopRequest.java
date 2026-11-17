package com.sogeco.fleet.modules.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Point de livraison supplementaire d'une tournee, saisi a la creation de la mission. */
@Schema(description = "Point de livraison supplementaire, dans l'ordre de passage")
public record StopRequest(

        @NotBlank(message = "le nom du point est obligatoire")
        @Size(max = 150)
        String label,

        @NotNull(message = "la latitude du point est obligatoire")
        BigDecimal latitude,

        @NotNull(message = "la longitude du point est obligatoire")
        BigDecimal longitude
) {
}
