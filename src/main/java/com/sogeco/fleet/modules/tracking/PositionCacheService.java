package com.sogeco.fleet.modules.tracking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.sogeco.fleet.modules.tracking.dto.LivePosition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cache des dernieres positions et gestion de l'idempotence.
 *
 * La carte temps reel lit ici, jamais dans gps_positions : interroger
 * une table de 1,35 million de lignes pour afficher 11 marqueurs serait
 * absurde.
 *
 * Une panne de Redis ne doit pas interrompre l'ingestion : toutes les
 * operations echouent en silence et laissent le flux se poursuivre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionCacheService {

    private static final String POSITION_KEY = "vehicle:%d:position";
    private static final String IDEMPOTENCY_KEY = "telematics:seen:%s:%d";
    private static final String BROADCAST_KEY = "vehicle:%d:lastBroadcast";

    private static final Duration POSITION_TTL = Duration.ofDays(2);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    // LivePosition contient un Instant : sur les JDK recents, l'adaptateur
    // reflectif par defaut de Gson echoue a rendre accessible le champ prive
    // Instant#seconds (encapsulation forte des modules), et l'echec est
    // avale silencieusement par les catch RuntimeException ci-dessous —
    // le cache ne s'ecrivait donc jamais. Un adaptateur explicite (format
    // ISO-8601) evite toute dependance a la reflexion sur java.time.
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new TypeAdapter<Instant>() {
                @Override
                public void write(JsonWriter out, Instant value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                    } else {
                        out.value(value.toString());
                    }
                }

                @Override
                public Instant read(JsonReader in) throws IOException {
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    return Instant.parse(in.nextString());
                }
            })
            .create();

    // ------------------------------------------------------------------
    // Derniere position
    // ------------------------------------------------------------------

    public void store(LivePosition position) {
        try {
            redis.opsForValue().set(
                    POSITION_KEY.formatted(position.vehicleId()),
                    gson.toJson(position),
                    POSITION_TTL);
        } catch (RuntimeException e) {
            log.warn("Ecriture Redis impossible pour le camion {}", position.vehicleId(), e);
        }
    }

    public Optional<LivePosition> find(Long vehicleId) {
        try {
            String json = redis.opsForValue().get(POSITION_KEY.formatted(vehicleId));
            return json == null ? Optional.empty() : Optional.of(gson.fromJson(json, LivePosition.class));
        } catch (RuntimeException e) {
            log.warn("Lecture Redis impossible pour le camion {}", vehicleId, e);
            return Optional.empty();
        }
    }

    /** Toutes les dernieres positions connues, pour la carte. */
    public List<LivePosition> findAll() {
        List<LivePosition> positions = new ArrayList<>();
        try {
            Set<String> keys = redis.keys("vehicle:*:position");
            if (keys == null || keys.isEmpty()) {
                return positions;
            }
            List<String> values = redis.opsForValue().multiGet(keys);
            if (values != null) {
                values.stream()
                        .filter(java.util.Objects::nonNull)
                        .forEach(json -> positions.add(gson.fromJson(json, LivePosition.class)));
            }
        } catch (RuntimeException e) {
            log.warn("Lecture globale Redis impossible", e);
        }
        return positions;
    }

    // ------------------------------------------------------------------
    // Idempotence
    // ------------------------------------------------------------------

    /**
     * Vrai si la trame a deja ete vue. Les reseaux mobiles et les
     * rejeux de Traccar produisent des doublons : sans ce controle, une
     * meme position serait comptee deux fois dans le kilometrage.
     */
    public boolean alreadySeen(String deviceId, long epochSecond) {
        try {
            Boolean created = redis.opsForValue().setIfAbsent(
                    IDEMPOTENCY_KEY.formatted(deviceId, epochSecond), "1", IDEMPOTENCY_TTL);
            return Boolean.FALSE.equals(created);
        } catch (RuntimeException e) {
            // Redis indisponible : on laisse passer plutot que de bloquer.
            log.warn("Controle d'idempotence impossible", e);
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Limitation de debit de la diffusion
    // ------------------------------------------------------------------

    /** Vrai si la diffusion WebSocket est autorisee pour ce camion. */
    public boolean canBroadcast(Long vehicleId, int minimumSeconds) {
        try {
            Boolean created = redis.opsForValue().setIfAbsent(
                    BROADCAST_KEY.formatted(vehicleId), "1", Duration.ofSeconds(minimumSeconds));
            return Boolean.TRUE.equals(created);
        } catch (RuntimeException e) {
            return true;
        }
    }
}
