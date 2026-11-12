package com.sogeco.fleet.modules.routing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/geocoding")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Geocodage", description = "Recherche inverse pour la selection sur carte")
public class GeocodingController {

    private final GeocodingClient geocodingClient;

    public record ReverseGeocodeResponse(String placeName, String cityName, BigDecimal latitude, BigDecimal longitude) {
    }

    @GetMapping("/search")
    @Operation(summary = "Propose des lieux du Cameroun a partir d'un texte saisi (autocompletion)")
    @PreAuthorize("isAuthenticated()")
    public java.util.List<GeocodingClient.PlaceSuggestion> search(
            @RequestParam String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        return geocodingClient.search(q, city, region, lat, lng);
    }

    @GetMapping("/reverse")
    @Operation(summary = "Trouve le lieu connu le plus proche d'un point (clic sur la carte)")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReverseGeocodeResponse> reverse(@RequestParam BigDecimal lat, @RequestParam BigDecimal lng) {
        return geocodingClient.reverseGeocode(lat, lng)
                .map(place -> ResponseEntity.ok(
                        new ReverseGeocodeResponse(place.name(), place.locality(), place.latitude(), place.longitude())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
