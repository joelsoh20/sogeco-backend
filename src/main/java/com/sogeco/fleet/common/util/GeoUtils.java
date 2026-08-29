package com.sogeco.fleet.common.util;

import java.math.BigDecimal;

/**
 * Calculs geographiques : distances et appartenance a une zone.
 * Utilise par l'ingestion telematique (kilometrage incremental)
 * et par le georeperage (module 11).
 */
public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    /** Emprise approximative du Cameroun, pour le controle de saisie des coordonnees. */
    public static final double CAMEROON_MIN_LAT = 1.5;
    public static final double CAMEROON_MAX_LAT = 13.5;
    public static final double CAMEROON_MIN_LNG = 8.3;
    public static final double CAMEROON_MAX_LNG = 16.5;

    private GeoUtils() {
    }

    /** Distance orthodromique en kilometres (formule de Haversine). */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return 0d;
        return distanceKm(lat1.doubleValue(), lng1.doubleValue(), lat2.doubleValue(), lng2.doubleValue());
    }

    /** Coordonnees situees dans l'emprise du Cameroun (avertissement, non bloquant). */
    public static boolean isInCameroon(double lat, double lng) {
        return lat >= CAMEROON_MIN_LAT && lat <= CAMEROON_MAX_LAT
                && lng >= CAMEROON_MIN_LNG && lng <= CAMEROON_MAX_LNG;
    }

    /** Coordonnees geographiquement valides. */
    public static boolean isValidCoordinate(double lat, double lng) {
        return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }

    /**
     * Test d'appartenance a un polygone (algorithme du lancer de rayon).
     * Utilise pour le georeperage : polygon[i] = {lat, lng}.
     */
    public static boolean isInsidePolygon(double lat, double lng, double[][] polygon) {
        if (polygon == null || polygon.length < 3) return false;
        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            double latI = polygon[i][0], lngI = polygon[i][1];
            double latJ = polygon[j][0], lngJ = polygon[j][1];
            boolean intersects = ((lngI > lng) != (lngJ > lng))
                    && (lat < (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI);
            if (intersects) inside = !inside;
        }
        return inside;
    }

    /**
     * Vitesse implicite entre deux positions, en km/h.
     * Sert au controle de vraisemblance des trames (RG-3.6).
     */
    public static double impliedSpeedKmh(double lat1, double lng1, double lat2, double lng2, long seconds) {
        if (seconds <= 0) return 0d;
        return distanceKm(lat1, lng1, lat2, lng2) / (seconds / 3600.0);
    }
}
