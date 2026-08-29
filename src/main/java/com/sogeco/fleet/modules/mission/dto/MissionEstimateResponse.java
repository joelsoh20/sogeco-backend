package com.sogeco.fleet.modules.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Estimation de distance et de carburant pour le formulaire de creation
 * de mission, avant meme que le trajet ne soit reellement parcouru.
 *
 * "source" indique la fiabilite du chiffre :
 *  - CORRIDOR_REFERENCE : distance mesuree sur des trajets reels, la plus fiable ;
 *  - ROUTING_API : distance routiere reelle calculee par OpenRouteService
 *    (profil poids lourd) — fiable, mais pas encore validee sur le terrain ;
 *  - SITE_COORDINATES / CITY_COORDINATES : repli si ORS n'est pas configure ou
 *    indisponible — estimation a vol d'oiseau (Haversine), majoree de 20% ;
 *  - INDISPONIBLE : donnees insuffisantes (coordonnees manquantes).
 *
 * "routeGeometry" : suite de points [latitude, longitude] du trace routier
 * reel — uniquement disponible quand source = ROUTING_API. Absent (null)
 * pour les autres sources : la carte cote client se rabat alors sur une
 * simple ligne droite entre origine et destination.
 */
@Schema(description = "Estimation de distance et de consommation avant creation de la mission")
public record MissionEstimateResponse(
        BigDecimal distanceKm,
        BigDecimal estimatedFuelLiters,
        String source,
        List<double[]> routeGeometry
) {
}
