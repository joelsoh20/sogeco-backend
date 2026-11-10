package com.sogeco.fleet.modules.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private final RestClient nominatimClient;
    private final String apiKey;
    private final boolean enabled;

    public GeocodingClient(
            @Value("${sogeco.ors.api-key:}") String apiKey,
            @Value("${sogeco.ors.geocode-base-url}") String geocodeBaseUrl) {
        this.apiKey = apiKey;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder().baseUrl(geocodeBaseUrl).build();
        // Nominatim (OSM public, gratuit) : bien meilleure couverture des quartiers
        // camerounais que l'index Pelias d'ORS pour ce pays — verifie manuellement
        // (Yaounde, Douala) — utilise en repli uniquement, jamais en premiere passe :
        // politique d'usage Nominatim stricte (User-Agent obligatoire, ~1 req/s).
        this.nominatimClient = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "SogecoFleetManager/1.0 (contact: contact@sogeco.cm)")
                .build();
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

    /** Une proposition de la recherche par texte : lieu, ville, coordonnees. */
    public record PlaceSuggestion(String label, String placeName, String cityName,
                                  BigDecimal latitude, BigDecimal longitude) {
    }

    // Politique d'usage Nominatim : 1 requete/seconde au plus, donc intervalle minimal entre
    // deux appels et petit cache (la saisie progressive repete souvent la meme recherche).
    private static final long NOMINATIM_MIN_INTERVAL_MS = 1100;
    private static final long SEARCH_CACHE_TTL_MS = 10 * 60 * 1000;
    private final Object nominatimLock = new Object();
    private long lastNominatimCall = 0;
    private final java.util.Map<String, CachedSearch> searchCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedSearch(long at, List<PlaceSuggestion> results) {
    }

    /**
     * Recherche de lieux par texte libre (autocompletion facon Google Maps), via Nominatim,
     * restreinte au Cameroun. Nominatim tolere les variantes d'orthographe courantes.
     * La ville est resolue comme pour le geocodage inverse (arrondissement ramene a la ville).
     * Echec reseau ou aucun resultat : liste vide, jamais d'exception.
     */
    public List<PlaceSuggestion> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 3) {
            return List.of();
        }
        String key = q.toLowerCase(java.util.Locale.ROOT);
        CachedSearch cached = searchCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.at() < SEARCH_CACHE_TTL_MS) {
            return cached.results();
        }
        try {
            List<NominatimSearchResult> raw;
            synchronized (nominatimLock) {
                long wait = NOMINATIM_MIN_INTERVAL_MS - (System.currentTimeMillis() - lastNominatimCall);
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                lastNominatimCall = System.currentTimeMillis();
                raw = nominatimClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/search")
                                .queryParam("q", q)
                                .queryParam("format", "jsonv2")
                                .queryParam("countrycodes", "cm")
                                .queryParam("addressdetails", 1)
                                .queryParam("limit", 8)
                                .queryParam("accept-language", "fr")
                                .build())
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<List<NominatimSearchResult>>() { });
            }
            List<PlaceSuggestion> results = new java.util.ArrayList<>();
            for (NominatimSearchResult r : raw == null ? List.<NominatimSearchResult>of() : raw) {
                // Les limites administratives (communautes urbaines, departements) ne sont pas des lieux de livraison.
                if (r.lat() == null || r.lon() == null || "boundary".equals(r.category())) {
                    continue;
                }
                String city = resolveCityFromNominatim(r.address());
                if (city == null && r.address() != null) {
                    city = stripArrondissement(firstNonBlank(r.address().county(), r.address().stateDistrict()));
                }
                if (city == null) {
                    continue;
                }
                String place = firstNonBlank(r.name(), resolvePlaceFromNominatim(r.address()), city);
                if (isArrondissementName(place)) {
                    place = city;
                }
                results.add(new PlaceSuggestion(
                        place.equalsIgnoreCase(city) ? city : place + ", " + city,
                        place, city, r.lat(), r.lon()));
            }
            searchCache.put(key, new CachedSearch(System.currentTimeMillis(), results));
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            log.warn("Recherche de lieux Nominatim en echec pour « {} » : {}", q, e.getMessage());
            return List.of();
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
     * Couches Pelias correspondant a un quartier/localite — les memes etiquettes
     * en gras que celles dessinees sur les tuiles OpenStreetMap. Une echoppe, un
     * hotel ou une ecole (couche "venue") est le point le plus proche bien plus
     * souvent qu'un quartier, mais ce n'est jamais ce que l'etiquette de la carte
     * annonce a l'endroit clique — d'ou la priorite absolue a ces couches avant
     * meme le filtre "lieu notable" (way/relation) plus bas.
     */
    private static final java.util.Set<String> PLACE_LAYERS =
            java.util.Set.of("neighbourhood", "locality", "borough", "localadmin", "macrohood", "microhood");
    private static final double PLACE_LAYER_ABSOLUTE_CAP_KM = 2.0;

    /**
     * Trouve le lieu connu le plus proche d'un point cliqué sur la carte —
     * meme moteur que les etiquettes de quartier affichees sur les tuiles
     * OpenStreetMap, donc coherent avec ce que l'utilisateur voit et clique.
     *
     * Ville et quartier sont resolus independamment (l'un peut manquer sans
     * bloquer l'autre) puis combines :
     *  - ville : couche ORS "locality/borough/..." la plus proche, sinon
     *    l'adresse structuree Nominatim (city_district reconnu comme
     *    arrondissement en priorite, sinon city/town/municipality) ;
     *  - quartier : le meilleur candidat ORS/Nominatim (suburb/neighbourhood/
     *    quarter/village), sinon la ville elle-meme (le clic est dans le
     *    centre, sans decoupage plus fin connu dans aucune des deux sources).
     */
    public Optional<ReversePlace> reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        if (!enabled) {
            return Optional.empty();
        }

        try {
            Optional<ReverseFeature> orsPlace = fetchReverse(latitude, longitude, PLACE_LAYERS)
                    .filter(f -> f.properties().distance() != null
                            && f.properties().distance().doubleValue() <= PLACE_LAYER_ABSOLUTE_CAP_KM);

            String cityFromOrs = orsPlace.map(f -> f.properties().locality())
                    .filter(s -> s != null && !s.isBlank())
                    .map(GeocodingClient::stripArrondissement)
                    .orElse(null);

            NominatimAddress nominatim = cityFromOrs != null
                    ? null
                    : fetchNominatimAddress(latitude, longitude).orElse(null);

            String cityName = cityFromOrs != null ? cityFromOrs : resolveCityFromNominatim(nominatim);
            if (cityName == null) {
                return legacyNotableFallback(latitude, longitude);
            }

            String placeName = null;
            if (orsPlace.isPresent() && !isArrondissementName(orsPlace.get().properties().name())) {
                placeName = orsPlace.get().properties().name();
            }

            if (placeName == null) {
                if (nominatim == null) {
                    nominatim = fetchNominatimAddress(latitude, longitude).orElse(null);
                }
                placeName = resolvePlaceFromNominatim(nominatim);
            }

            if (placeName == null) {
                placeName = cityName;
            }

            return Optional.of(new ReversePlace(placeName, cityName, latitude, longitude));

        } catch (Exception e) {
            log.warn("Geocodage inverse en echec pour ({}, {}) : {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Dernier recours quand ni ORS ni Nominatim ne rattachent le point a une
     * ville connue (zones tres peu cartographiees) : le point le plus proche
     * tous types confondus, en privilegiant un lieu notable (way/relation :
     * marche, hopital...) pertinent pour une livraison, plutot qu'une echoppe
     * ou un point isole qui passerait devant par pure proximite.
     */
    private Optional<ReversePlace> legacyNotableFallback(BigDecimal latitude, BigDecimal longitude) {
        List<ReverseFeature> features = fetchAllReverse(latitude, longitude);
        if (features.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal nearestDistance = features.get(0).properties().distance();
        double threshold = nearestDistance == null
                ? NOTABLE_PLACE_ABSOLUTE_CAP_KM
                : Math.min(nearestDistance.doubleValue() * 2 + 0.03, NOTABLE_PLACE_ABSOLUTE_CAP_KM);

        ReverseFeature feature = features.stream()
                .filter(GeocodingClient::isNotablePlace)
                .filter(f -> f.properties().distance() != null
                        && f.properties().distance().doubleValue() <= threshold)
                .findFirst()
                .orElse(features.get(0));

        return Optional.of(toReversePlace(feature));
    }

    private Optional<ReverseFeature> fetchReverse(BigDecimal latitude, BigDecimal longitude,
                                                   java.util.Set<String> layers) {
        ReverseResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/geocode/reverse")
                        .queryParam("api_key", apiKey)
                        .queryParam("point.lat", latitude)
                        .queryParam("point.lon", longitude)
                        .queryParam("boundary.country", "CMR")
                        .queryParam("layers", String.join(",", layers))
                        .queryParam("size", 1)
                        .build())
                .retrieve()
                .body(ReverseResponse.class);
        if (response == null || response.features() == null || response.features().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(response.features().get(0));
    }

    private List<ReverseFeature> fetchAllReverse(BigDecimal latitude, BigDecimal longitude) {
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
        return response == null || response.features() == null ? List.of() : response.features();
    }

    private static ReversePlace toReversePlace(ReverseFeature feature) {
        List<BigDecimal> coordinates = feature.geometry().coordinates();
        return new ReversePlace(
                feature.properties().name(),
                feature.properties().locality(),
                coordinates.get(1), coordinates.get(0));
    }

    private Optional<NominatimAddress> fetchNominatimAddress(BigDecimal latitude, BigDecimal longitude) {
        try {
            NominatimResponse response = nominatimClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("format", "json")
                            .queryParam("zoom", 16)
                            .queryParam("addressdetails", 1)
                            .queryParam("accept-language", "fr")
                            .build())
                    .retrieve()
                    .body(NominatimResponse.class);
            return response == null ? Optional.empty() : Optional.ofNullable(response.address());
        } catch (Exception e) {
            log.warn("Geocodage inverse Nominatim en echec pour ({}, {}) : {}", latitude, longitude, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * En peripherie, Nominatim rattache parfois le point a la commune rurale
     * voisine ("Okola", "Lekie"...) plutot qu'a la ville a laquelle son propre
     * city_district ("Yaounde II") le rattache deja clairement — un city_district
     * reconnu comme arrondissement prime donc sur city/town/municipality bruts.
     */
    private static String resolveCityFromNominatim(NominatimAddress address) {
        if (address == null) {
            return null;
        }
        String districtCity = arrondissementBaseName(address.cityDistrict());
        String city = firstNonBlank(districtCity, address.city(), address.town(), address.municipality());
        return stripArrondissement(city);
    }

    /**
     * Un city_district lui-meme reconnu comme arrondissement ("Yaounde VII") duplique
     * l'information de ville, ce n'est jamais un nom de quartier utilisable.
     */
    private static String resolvePlaceFromNominatim(NominatimAddress address) {
        if (address == null) {
            return null;
        }
        String districtCandidate = isArrondissementName(address.cityDistrict()) ? null : address.cityDistrict();
        return firstNonBlank(address.suburb(), address.neighbourhood(), address.quarter(),
                address.village(), districtCandidate);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Nominatim renvoie parfois la ville sous forme "Douala III" ou "Yaoundé VI"
     * (l'arrondissement administratif) alors que le referentiel de l'application
     * ne connait que le nom de ville simple ("Douala", "Yaoundé"). On retire donc
     * le suffixe en chiffres romains pour retrouver le nom de ville de base.
     */
    private static final java.util.regex.Pattern ARRONDISSEMENT_SUFFIX =
            java.util.regex.Pattern.compile("^(.*\\S)\\s+(?:I|II|III|IV|V|VI|VII|VIII|IX|X)$");

    private static String stripArrondissement(String city) {
        if (city == null) {
            return null;
        }
        java.util.regex.Matcher matcher = ARRONDISSEMENT_SUFFIX.matcher(city.trim());
        return matcher.matches() ? matcher.group(1) : city;
    }

    private static boolean isArrondissementName(String name) {
        return name != null && ARRONDISSEMENT_SUFFIX.matcher(name.trim()).matches();
    }

    /** Nom de base si {@code value} est bien un arrondissement ("Yaounde VII" -> "Yaounde"), sinon null. */
    private static String arrondissementBaseName(String value) {
        if (value == null) {
            return null;
        }
        java.util.regex.Matcher matcher = ARRONDISSEMENT_SUFFIX.matcher(value.trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private record NominatimSearchResult(String name, String category, BigDecimal lat, BigDecimal lon, NominatimAddress address) {
    }

    private record NominatimResponse(NominatimAddress address) {
    }

    private record NominatimAddress(
            String suburb,
            String neighbourhood,
            String quarter,
            String village,
            @JsonProperty("city_district") String cityDistrict,
            String city,
            String town,
            String municipality,
            String county,
            @JsonProperty("state_district") String stateDistrict) {
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
