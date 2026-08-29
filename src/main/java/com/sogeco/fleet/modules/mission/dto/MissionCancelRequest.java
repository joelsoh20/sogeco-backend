package com.sogeco.fleet.modules.mission.dto;

import com.sogeco.fleet.common.enums.CancellationReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MissionCancelRequest(

        @NotNull(message = "le motif d'annulation est obligatoire")
        CancellationReason reason,

        @Size(max = 500)
        String comment
) {
}
