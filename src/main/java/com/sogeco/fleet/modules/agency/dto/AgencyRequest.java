package com.sogeco.fleet.modules.agency.dto;

import com.sogeco.fleet.common.enums.SiteType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Creation ou modification d'un site")
public record AgencyRequest(

        @Size(max = 20)
        @Schema(description = "Optionnel — genere automatiquement (ville + type de site) si absent", example = "DLA-BPC")
        String code,

        @Size(max = 120)
        @Schema(description = "Optionnel — genere automatiquement (type de site + ville) si absent", example = "BP Cite")
        String name,

        @NotNull(message = "la ville est obligatoire")
        Long cityId,

        @NotNull(message = "le quartier est obligatoire")
        @Schema(description = "Localisation la plus fine du site, pour affiner le calcul des distances de mission")
        Long quartierId,

        @NotNull(message = "le type de site est obligatoire")
        SiteType siteType,

        @Size(max = 255)
        String address,

        @Size(max = 30)
        String phone,

        @Schema(description = "Couple colle depuis Google Maps", example = "4.0511, 9.7679")
        String coordinates
) {
}
