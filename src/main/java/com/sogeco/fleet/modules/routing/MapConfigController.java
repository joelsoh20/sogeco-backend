package com.sogeco.fleet.modules.routing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indique au frontend quel fond de carte afficher. La bascule OSM / Google se
 * fait par configuration (sogeco.map.provider), sans toucher au metier.
 *
 * La cle exposee est une cle NAVIGATEUR (Maps JavaScript API), publique par
 * nature : sa protection repose sur les restrictions posees dans Google Cloud
 * Console (referent HTTP + API autorisee), pas sur le secret. L'endpoint reste
 * authentifie pour qu'elle ne soit pas servie a n'importe quel visiteur.
 */
@RestController
@RequestMapping("/api/v1/map")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Carte", description = "Configuration du fond de carte")
public class MapConfigController {

    private final String configuredProvider;
    private final String googleApiKey;
    private final String googleMapId;

    public MapConfigController(
            @Value("${sogeco.map.provider:osm}") String configuredProvider,
            @Value("${sogeco.map.google.api-key:}") String googleApiKey,
            @Value("${sogeco.map.google.map-id:}") String googleMapId) {
        this.configuredProvider = configuredProvider;
        this.googleApiKey = googleApiKey;
        this.googleMapId = googleMapId;
    }

    /** googleMapsApiKey / googleMapId : null si le fournisseur effectif n'est pas Google. */
    public record MapConfigResponse(String provider, String googleMapsApiKey, String googleMapId) {
    }

    @GetMapping("/config")
    @Operation(summary = "Fournisseur de fond de carte et cle navigateur associee")
    @PreAuthorize("isAuthenticated()")
    public MapConfigResponse config() {
        boolean google = "google".equalsIgnoreCase(configuredProvider) && !googleApiKey.isBlank();
        if (!google) {
            return new MapConfigResponse("osm", null, null);
        }
        return new MapConfigResponse("google", googleApiKey, googleMapId.isBlank() ? null : googleMapId);
    }
}
