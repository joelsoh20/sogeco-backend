package com.sogeco.fleet.modules.mission.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Chargement complementaire signale en cours de route : le camion doit passer par un autre site avant sa destination. */
public record MissionDetourRequest(

        @NotNull(message = "le site a visiter est obligatoire")
        Long agencyId,

        @Size(max = 255)
        String notes
) {
}
