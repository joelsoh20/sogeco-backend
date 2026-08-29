package com.sogeco.fleet.modules.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Geocodage (recherche par nom, et inverse par coordonnees), base Pelias.
 *
 * Base-url DISTINCTE de {@link OpenRouteServiceClient} (directions) : verifie
 * manuellement le 15/08/2026, /geocode/search et /geocode/reverse renvoient
 * 404 sur api.heigit.org (seules les directions y sont migrees), alors
 * qu'ils repondent normalement sur l'ancien domaine api.openrouteservice.org.
 * Ne pas fusionner les deux base-url tant que ce n'est pas corrige cote ORS.
 *
 * Cle absente, lieu introuvable, ou appel en echec (reseau, quota) :
 * Optional.empty(), jamais d'exception. Comme pour le routage, c'est un
 * raffinement qui remplit un champ manquant, jamais un prerequis.
 */
@Slf4j
@Service
public class GeocodingClient {

    private final RestClient restClient;
    private final String apiKey;
    private final boolean enabled;

    public GeocodingClient(
            @Value("${sogeco.ors.api-key:}") String apiKey,
            @Value("${sogeco.ors.geocode-base-url}") String geocodeBaseUrl) {
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder().baseUrl(geocodeBaseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }

    /** Recherche par texte libre, restreinte au Cameroun pour eviter les faux positifs homonymes. */
    public Optional<GeoPoint> geocode(String query) {
        if (!enabled || query == null || query.isBlank()) {
            return Optional.empty();
        }

        try {
            SearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("text", query)
                            .queryParam("boundary.country", "CMR")
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .body(SearchResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                log.warn("Geocodage sans resultat pour « {} »", query);
                return Optional.empty();
            }

            List<BigDecimal> coordinates = response.features().get(0).geometry().coordinates();
            // GeoJSON : [longitude, latitude], dans cet ordre.
            return Optional.of(new GeoPoint(coordinates.get(1), coordinates.get(0)));

        } catch (Exception e) {
            log.warn("Appel de geocodage en echec pour « {} » : {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    /** name : le lieu le plus proche du point (quartier/venue/rue...) ; locality : sa ville. */
    public record ReversePlace(String name, String locality, BigDecimal latitude, BigDecimal longitude) {
    }

    /** Un marché, un hopital, un centre commercial... est souvent digitalise en zone (way/relation)
     * plutot qu'en simple point (node), a la difference d'une echoppe ou d'un commerce isole. Au-dela
     * du point le plus proche, on privilegie donc ces lieux notables s'ils restent tout pres du clic. */
    private static final double NOTABLE_PLACE_ABSOLUTE_CAP_KM = 0.3;

    /**
     * Trouve le lieu connu le plus proche d'un point cliqué sur la carte —
     * meme moteur que les etiquettes de quartier affichees sur les tuiles
     * OpenStreetMap, donc coherent avec ce que l'utilisateur voit et clique.
     *
     * Pelias classe par proximite brute : au marche, la echoppe la plus proche
     * du clic passe souvent devant le marche lui-meme (way, donc plus etendu
     * mais centroid legerement plus loin). On demande plusieurs candidats et on
     * privilegie le premier lieu "notable" (way/relation) tout proche, pertinent
     * pour une livraison, plutot que le point brut le plus proche.
     */
    public Optional<ReversePlace> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            ReverseResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/geocode/reverse")
                            .queryParam("api_key", apiKey)
                            .queryParam("point.lat", latitude)
                            .queryParam("point.lon", longitude)
                            .queryParam("boundary.country", "CMR")
                            .queryParam("size", 5)
                            .build())
                    .retrieve()
                    .body(ReverseResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                return Optional.empty();
            }

            List<ReverseFeature> features = response.features();
            BigDecimal nearestDistance = features.get(0).properties().distance();
            // Marge relative au point le plus proche (double sa distance + 30m) : assez pour
            // rattraper un marche/hopital juste a cote, pas assez pour sauter vers un batiment
            // notable mais sans rapport, bien plus loin que ce que l'utilisateur a vise.
            double threshold = nearestDistance == null
                    ? NOTABLE_PLACE_ABSOLUTE_CAP_KM
                    : Math.min(nearestDistance.doubleValue() * 2 + 0.03, NOTABLE_PLACE_ABSOLUTE_CAP_KM);

            ReverseFeature feature = features.stream()
                    .filter(GeocodingClient::isNotablePlace)
                    .filter(f -> f.properties().distance() != null
                            && f.properties().distance().doubleValue() <= threshold)
                    .findFirst()
                    .orElse(features.get(0));

            List<BigDecimal> coordinates = feature.geometry().coordinates();

            return Optional.of(new ReversePlace(
                    feature.properties().name(),
                    feature.properties().locality(),
                    coordinates.get(1), coordinates.get(0)));

        } catch (Exception e) {
            log.warn("Geocodage inverse en echec pour ({}, {}) : {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isNotablePlace(ReverseFeature feature) {
        String id = feature.properties().id();
        return id != null && (id.startsWith("way/") || id.startsWith("relation/"));
    }

    // ------------------------------------------------------------------
    // Format d'echange Pelias (sous-ensemble minimal)
    // ------------------------------------------------------------------

    private record SearchResponse(List<Feature> features) {
    }

    private record Feature(Geometry geometry) {
    }

    private record Geometry(List<BigDecimal> coordinates) {
    }

    private record ReverseResponse(List<ReverseFeature> features) {
    }

    private record ReverseFeature(ReverseProperties properties, Geometry geometry) {
    }

    private record ReverseProperties(String name, String locality, String id, BigDecimal distance) {
    }
}
