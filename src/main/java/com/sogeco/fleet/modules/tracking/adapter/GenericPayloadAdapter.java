package com.sogeco.fleet.modules.tracking.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur generique, pour un prestataire dont le format suit les
 * conventions courantes : champs a plat, vitesse deja en km/h.
 *
 * Il accepte un objet unique ou un tableau de positions, et tolere
 * plusieurs graphies pour chaque champ. C'est le point de depart quand
 * on branche un nouveau fournisseur : si son format s'en ecarte trop,
 * on ecrit un adaptateur dedie sans toucher au reste.
 */
@Slf4j
@Component
public class GenericPayloadAdapter implements TelematicsPayloadAdapter {

    public static final String PROVIDER = "generic";

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public List<TelematicsPayload> parse(String rawBody) {
        try {
            JsonElement root = JsonParser.parseString(rawBody);
            List<TelematicsPayload> payloads = new ArrayList<>();

            if (root.isJsonArray()) {
                JsonArray array = root.getAsJsonArray();
                array.forEach(element -> payloads.add(toPayload(element.getAsJsonObject())));
            } else {
                payloads.add(toPayload(root.getAsJsonObject()));
            }

            return payloads;

        } catch (RuntimeException e) {
            log.warn("Trame generique illisible : {}", e.getMessage());
            throw new BusinessException("TELEMATICS_FORMAT",
                    "Trame illisible", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    private TelematicsPayload toPayload(JsonObject json) {
        return TelematicsPayload.builder()
                .deviceId(text(json, "deviceId", "imei", "uniqueId", "device_id"))
                .recordedAt(instant(json, "timestamp", "recordedAt", "time", "datetime"))
                .latitude(decimal(json, "latitude", "lat"))
                .longitude(decimal(json, "longitude", "lon", "lng"))
                .speedKmh(decimal(json, "speed", "speedKmh"))
                .heading(decimal(json, "heading", "course", "bearing"))
                .altitude(decimal(json, "altitude", "alt"))
                .ignitionOn(bool(json, "ignition", "ignitionOn", "acc"))
                .odometerKm(decimal(json, "odometer", "odometerKm"))
                .fuelLevelPercent(decimal(json, "fuelLevelPercent", "fuelPercent"))
                .fuelLevelLiters(decimal(json, "fuelLevelLiters", "fuel"))
                .engineTemperature(decimal(json, "engineTemperature", "coolantTemp"))
                .engineRpm(integer(json, "engineRpm", "rpm"))
                .batteryVoltage(decimal(json, "batteryVoltage", "power"))
                .errorCodes(codes(json))
                .protocol(text(json, "protocol"))
                .valid(bool(json, "valid"))
                .alarm(text(json, "alarm", "event"))
                .build();
    }

    private List<String> codes(JsonObject json) {
        JsonElement element = first(json, "errorCodes", "dtcs");
        if (element == null) {
            return List.of();
        }
        if (element.isJsonArray()) {
            List<String> codes = new ArrayList<>();
            element.getAsJsonArray().forEach(item -> codes.add(item.getAsString()));
            return codes;
        }
        String raw = element.getAsString();
        return raw.isBlank() ? List.of() : List.of(raw.split("[,\\s]+"));
    }

    private String text(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        return element == null ? null : element.getAsString();
    }

    private BigDecimal decimal(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        try {
            return element == null ? null : element.getAsBigDecimal();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer integer(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        try {
            return element == null ? null : element.getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Boolean bool(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        try {
            return element == null ? null : element.getAsBoolean();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Instant instant(JsonObject json, String... keys) {
        JsonElement element = first(json, keys);
        if (element == null) {
            return Instant.now();
        }
        try {
            // Horodatage Unix, en secondes ou en millisecondes
            if (element.getAsJsonPrimitive().isNumber()) {
                long value = element.getAsLong();
                return value > 100_000_000_000L
                        ? Instant.ofEpochMilli(value)
                        : Instant.ofEpochSecond(value);
            }
            return java.time.OffsetDateTime.parse(element.getAsString()).toInstant();
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private JsonElement first(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key);
            }
        }
        return null;
    }
}
