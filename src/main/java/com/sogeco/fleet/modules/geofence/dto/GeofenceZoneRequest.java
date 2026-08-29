package com.sogeco.fleet.modules.geofence.dto;

import com.sogeco.fleet.common.enums.GeofenceZoneType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(description = "Zone de georeperage")
public record GeofenceZoneRequest(

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 120)
        @Schema(example = "Douala Centre")
        String name,

        @NotNull(message = "le type de zone est obligatoire")
        GeofenceZoneType zoneType,

        @NotBlank(message = "le polygone est obligatoire")
        @Schema(description = "Polygone GeoJSON. Attention : les coordonnees sont en [longitude, latitude].",
                example = "{\"type\":\"Polygon\",\"coordinates\":[[[9.70,4.02],[9.80,4.02],[9.80,4.09],[9.70,4.09],[9.70,4.02]]]}")
        String polygonGeojson,

        Long cityId,
        Boolean alertOnEntry,
        Boolean alertOnExit,

        @Size(max = 255)
        String description,

        @Schema(description = "Camions concernes. Vide : la zone s'applique a tout le parc.")
        Set<Long> vehicleIds
) {
}
