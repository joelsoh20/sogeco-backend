package com.sogeco.fleet.modules.tracking.dto;

import java.time.Instant;
import java.util.List;

/**
 * Trajet d'un camion redessine sur les routes (OpenRouteService), a la place des lignes droites
 * entre points GPS.
 *
 * Trois sortes de portions :
 *  - SNAPPED   : points GPS proches dans le temps, relies par l'itineraire routier reel ;
 *  - ESTIMATED : trou de reception (plus de quelques minutes sans point) : itineraire le plus court entre
 *                les deux points voisins, a presenter comme une estimation (le camion a pu passer ailleurs) ;
 *  - RAW       : OpenRouteService indisponible ou en echec pour cette portion : ligne droite brute.
 */
public record SnappedTrack(
        Long vehicleId,
        Instant from,
        Instant to,
        /** Releves GPS valides dans la periode. */
        int positionCount,
        /** Points retenus apres elimination de ceux qui sont a moins de quelques dizaines de metres du precedent. */
        int keptPoints,
        boolean snappingAvailable,
        /** Nombre de trous de reception (portions ESTIMATED ou RAW entre deux series de points). */
        int gapCount,
        List<Segment> segments,
        Endpoint start,
        Endpoint end
) {

    public enum Kind {
        SNAPPED, ESTIMATED, RAW
    }

    /** points : [latitude, longitude]. */
    public record Segment(Kind kind, List<double[]> points) {
    }

    public record Endpoint(Instant at, double latitude, double longitude) {
    }
}
