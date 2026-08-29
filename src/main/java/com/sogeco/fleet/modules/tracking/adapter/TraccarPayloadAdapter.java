package com.sogeco.fleet.modules.tracking.adapter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adaptateur Traccar.
 *
 * Format recu avec forward.type=json :
 *   { "device": { "uniqueId": "...", ... }, "position": { ... } }
 *
 * DEUX PIEGES traites ici :
 *
 * 1. La vitesse est exprimee en NOEUDS, convention NMEA. Sans la
 *    conversion x1,852, un camion a 95 km/h serait vu a 51 et l'alerte
 *    de vitesse excessive ne se declencherait jamais.
 *
 * 2. L'odometre est en METRES dans les attributs, pas en kilometres.
 */
@Slf4j
@Component
public class TraccarPayloadAdapter implements TelematicsPayloadAdapter {

    public static final String PROVIDER = "traccar";

    /** Un noeud vaut 1,852 km/h. */
    private static final BigDecimal KNOTS_TO_KMH = BigDecimal.valueOf(1.852);

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<TelematicsPayload> parse(String rawBody) {
        try {
            JsonObject root = JsonParser.parseString(rawBody).getAsJsonObject();

            JsonObject position = root.getAsJsonObject("position");
            if (position == null) {
                throw new BusinessException("TELEMATICS_FORMAT",
                        "Trame Traccar sans objet position", HttpStatus.UNPROCESSABLE_CONTENT);
            }

            JsonObject device = root.getAsJsonObject("device");
            JsonObject attributes = position.has("attributes")
                    ? position.getAsJsonObject("attributes") : new JsonObject();

            return List.of(TelematicsPayload.builder()
                    .deviceId(text(device, "uniqueId"))
                    .recordedAt(instant(position, "fixTime", "deviceTime", "serverTime"))
                    .latitude(decimal(position, "latitude"))
                    .longitude(decimal(position, "longitude"))
                    .speedKmh(knotsToKmh(decimal(position, "speed")))
                    .heading(decimal(position, "course"))
                    .altitude(decimal(position, "altitude"))
                    .protocol(text(position, "protocol"))
                    .valid(bool(position, "valid"))
                    // ---- Attributs, tous facultatifs ----
                    .ignitionOn(bool(attributes, "ignition"))
                    .odometerKm(metersToKm(decimal(attributes, "odometer", "totalDistance")))
                    .fuelLevelLiters(decimal(attributes, "fuel"))
                    .fuelLevelPercent(decimal(attributes, "fuelLevel"))
                    .engineTemperature(decimal(attributes, "coolantTemp", "engineTemp"))
                    .engineRpm(integer(attributes, "rpm"))
                    .batteryVoltage(decimal(attributes, "power", "battery"))
                    .engineHours(decimal(attributes, "hours"))
                    .errorCodes(errorCodes(attributes))
                    .alarm(text(attributes, "alarm"))
                    .build());

        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Trame Traccar illisible : {}", e.getMessage());
            throw new BusinessException("TELEMATICS_FORMAT",
                    "Trame Traccar illisible", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    // ------------------------------------------------------------------
    // Conversions
    // ------------------------------------------------------------------

    private BigDecimal knotsToKmh(BigDecimal knots) {
        return knots == null ? null : knots.multiply(KNOTS_TO_KMH).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal metersToKm(BigDecimal meters) {
        return meters == null ? null
                : meters.divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }

    /** Les codes defaut arrivent en chaine separee par des virgules. */
    private List<String> errorCodes(JsonObject attributes) {
        String raw = text(attributes, "dtcs", "dtc");
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("[,\\s]+"))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }

    // ------------------------------------------------------------------
    // Lecture defensive : un attribut absent ne doit jamais faire echouer
    // la trame entiere, seulement laisser le champ a null.
    // ------------------------------------------------------------------

    private String text(JsonObject object, String... keys) {
        JsonElement element = first(object, keys);
        return element == null ? null : element.getAsString();
    }

    private BigDecimal decimal(JsonObject object, String... keys) {
        JsonElement element = first(object, keys);
        try {
            return element == null ? null : element.getAsBigDecimal();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer integer(JsonObject object, String... keys) {
        JsonElement element = first(object, keys);
        try {
            return element == null ? null : element.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Boolean bool(JsonObject object, String... keys) {
        JsonElement element = first(object, keys);
        try {
            return element == null ? null : element.getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Instant instant(JsonObject object, String... keys) {
        String value = text(object, keys);
        if (value == null) {
            return Instant.now();
        }
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException e) {
            try {
                return Instant.parse(value);
            } catch (RuntimeException ignored) {
                log.debug("Horodatage illisible : {}", value);
                return Instant.now();
            }
        }
    }

    /** Premiere cle presente et non nulle parmi celles proposees. */
    private JsonElement first(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key);
            }
        }
        return null;
    }
}
