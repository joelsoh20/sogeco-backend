package com.sogeco.fleet.common.util;

import com.sogeco.fleet.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Analyse des coordonnees saisies par l'utilisateur.
 *
 * Le formulaire de site accepte un couple colle depuis Google Maps :
 *   "4.0511, 9.7679"   "4.0511,9.7679"   "4.0511 9.7679"
 *
 * Voir CDC technique, section 1.3 : trois modes de saisie, le collage
 * direct etant le plus pratique pour l'utilisateur.
 */
public final class CoordinateParser {

    public static final int SCALE = 7;

    private CoordinateParser() {
    }

    public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }

    /**
     * Decompose une chaine "latitude, longitude".
     * Rejette les valeurs hors des bornes geographiques.
     */
    public static GeoPoint parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Coordonnees vides");
        }

        String[] parts = raw.trim().replace(";", ",").split("[,\\s]+");
        if (parts.length != 2) {
            throw invalid("Format attendu : latitude, longitude (exemple : 4.0511, 9.7679)");
        }

        BigDecimal latitude = toDecimal(parts[0], "latitude");
        BigDecimal longitude = toDecimal(parts[1], "longitude");

        if (!GeoUtils.isValidCoordinate(latitude.doubleValue(), longitude.doubleValue())) {
            throw invalid("Coordonnees hors des bornes geographiques valides");
        }

        return new GeoPoint(latitude, longitude);
    }

    /**
     * Emprise du Cameroun : avertissement seulement, jamais bloquant.
     * Un client peut etre livre hors du pays.
     */
    public static boolean isOutsideCameroon(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return !GeoUtils.isInCameroon(latitude.doubleValue(), longitude.doubleValue());
    }

    private static BigDecimal toDecimal(String value, String field) {
        try {
            return new BigDecimal(value.trim()).setScale(SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw invalid("Valeur de %s illisible : %s".formatted(field, value));
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("RG-13.5", message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
