package com.sogeco.fleet.modules.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Client OpenRouteService (openrouteservice.org) : distance et duree
 * routieres reelles entre deux points, par opposition a la distance a
 * vol d'oiseau (Haversine) utilisee en repli.
 *
 * Profil driving-hgv (poids lourd) plutot que driving-car : ORS evite
 * alors les restrictions de gabarit connues du reseau, plus pertinent
 * pour une flotte de camions.
 *
 * Aucune cle configuree, ou l'appel echoue (reseau, quota, coordonnees
 * hors couverture) : on retombe silencieusement sur l'estimation a vol
 * d'oiseau plutot que de faire echouer la creation de mission — cet
 * appel est un raffinement, jamais un prerequis.
 */
@Slf4j
@Service
public class OpenRouteServiceClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String profile;
    private final boolean enabled;

    public OpenRouteServiceClient(
            @Value("${sogeco.ors.api-key:}") String apiKey,
            @Value("${sogeco.ors.base-url}") String baseUrl,
            @Value("${sogeco.ors.profile:driving-hgv}") String profile) {
        this.apiKey = apiKey;
        this.profile = profile;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** geometry : suite de points [latitude, longitude] du trace routier reel, pour affichage carte. */
    public record RoadRoute(BigDecimal distanceKm, Integer durationMinutes, List<double[]> geometry) {
    }

    /**
     * Coordonnees en (latitude, longitude) — reordonnees en (lng, lat) pour ORS en interne.
     *
     * Variante "/geojson" de l'API directions plutot que la reponse JSON
     * standard : la geometrie y est une liste de points exploitable
     * directement, alors que la reponse standard l'encode en polyline
     * (algorithme a decoder cote serveur pour rien, puisque la variante
     * geojson existe).
     */
    public Optional<RoadRoute> route(BigDecimal originLat, BigDecimal originLng,
                                     BigDecimal destLat, BigDecimal destLng) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            DirectionsRequest body = new DirectionsRequest(List.of(
                    List.of(originLng, originLat),
                    List.of(destLng, destLat)));

            GeoJsonResponse response = restClient.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeoJsonResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                log.warn("OpenRouteService : reponse sans itineraire pour ({}, {}) -> ({}, {})",
                        originLat, originLng, destLat, destLng);
                return Optional.empty();
            }

            Feature feature = response.features().get(0);
            Summary summary = feature.properties().summary();

            // GeoJSON ordonne [longitude, latitude] ; on reordonne en [latitude, longitude]
            // pour que l'appelant (et la carte cote frontend) n'ait pas a s'en souvenir.
            List<double[]> geometry = feature.geometry().coordinates().stream()
                    .map(point -> new double[]{point.get(1), point.get(0)})
                    .toList();

            return Optional.of(new RoadRoute(
                    BigDecimal.valueOf(summary.distance() / 1000.0).setScale(2, RoundingMode.HALF_UP),
                    (int) Math.round(summary.duration() / 60.0),
                    geometry));

        } catch (Exception e) {
            log.warn("Appel OpenRouteService en echec, repli sur l'estimation a vol d'oiseau : {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------
    // Format d'echange ORS (sous-ensemble minimal : distance/duree/trace)
    // ------------------------------------------------------------------

    private record DirectionsRequest(List<List<BigDecimal>> coordinates) {
    }

    private record GeoJsonResponse(List<Feature> features) {
    }

    private record Feature(Properties properties, Geometry geometry) {
    }

    private record Properties(Summary summary) {
    }

    private record Geometry(List<List<Double>> coordinates) {
    }

    private record Summary(double distance, double duration) {
    }
}
