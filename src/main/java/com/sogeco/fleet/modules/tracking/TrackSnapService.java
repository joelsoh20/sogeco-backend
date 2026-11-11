package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.modules.routing.OpenRouteServiceClient;
import com.sogeco.fleet.modules.tracking.dto.SnappedTrack;
import com.sogeco.fleet.modules.tracking.dto.SnappedTrack.Endpoint;
import com.sogeco.fleet.modules.tracking.dto.SnappedTrack.Kind;
import com.sogeco.fleet.modules.tracking.dto.SnappedTrack.Segment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redessine le trajet GPS d'un camion sur les routes, via OpenRouteService.
 *
 * Methode : les points GPS sont d'abord allegis (un point a moins de 40 m du precedent n'apporte rien et
 * consommerait le quota), puis decoupes en series continues (au plus 3 minutes entre deux points). Chaque
 * serie est envoyee a ORS par paquets de 40 points : l'API renvoie l'itineraire routier qui passe par ces
 * points. Entre deux series (trou de reception), l'itineraire le plus court entre les deux points voisins
 * est trace a part, marque ESTIMATED.
 *
 * Quota ORS : au plus MAX_CALLS appels par trajet ; au-dela, ou si ORS echoue, la portion reste en ligne
 * droite (RAW). Les itineraires deja calcules sont gardes en memoire : l'historique passe ne change pas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackSnapService {

    private static final double MIN_STEP_METERS = 40;
    private static final long RUN_GAP_SECONDS = 180;
    private static final double MIN_GAP_METERS = 100;
    private static final int CHUNK_POINTS = 40;
    private static final int MAX_CALLS = 25;
    private static final int CACHE_LIMIT = 2000;

    private final GpsPositionRepository positionRepository;
    private final OpenRouteServiceClient ors;

    private final Map<String, List<double[]>> cache = new ConcurrentHashMap<>();

    private record P(Instant at, double lat, double lng) {
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_HISTORY_READ')")
    public SnappedTrack snap(Long vehicleId, Instant from, Instant to) {
        List<P> all = positionRepository.findHistory(vehicleId, from, to).stream()
                .filter(p -> Boolean.TRUE.equals(p.getValid()))
                .map(p -> new P(p.getRecordedAt(), p.getLatitude().doubleValue(), p.getLongitude().doubleValue()))
                .toList();

        List<P> kept = thin(all);
        List<Segment> segments = new ArrayList<>();
        int[] calls = {0};
        int gaps = 0;

        // Series continues : coupure des qu'un trou de reception depasse RUN_GAP_SECONDS.
        List<List<P>> runs = new ArrayList<>();
        List<P> current = new ArrayList<>();
        for (P p : kept) {
            if (!current.isEmpty()
                    && p.at().getEpochSecond() - current.get(current.size() - 1).at().getEpochSecond() > RUN_GAP_SECONDS) {
                runs.add(current);
                current = new ArrayList<>();
            }
            current.add(p);
        }
        if (!current.isEmpty()) {
            runs.add(current);
        }

        for (int r = 0; r < runs.size(); r++) {
            List<P> run = runs.get(r);
            for (int i = 0; i + 1 < run.size(); i += CHUNK_POINTS - 1) {
                List<P> chunk = run.subList(i, Math.min(i + CHUNK_POINTS, run.size()));
                if (chunk.size() >= 2) {
                    segments.add(roadSegment(chunk, Kind.SNAPPED, calls));
                }
            }
            if (r + 1 < runs.size()) {
                P a = run.get(run.size() - 1);
                P b = runs.get(r + 1).get(0);
                if (distanceMeters(a, b) >= MIN_GAP_METERS) {
                    gaps++;
                    segments.add(roadSegment(List.of(a, b), Kind.ESTIMATED, calls));
                }
            }
        }

        P first = kept.isEmpty() ? null : kept.get(0);
        P last = kept.isEmpty() ? null : kept.get(kept.size() - 1);
        return new SnappedTrack(vehicleId, from, to, all.size(), kept.size(), ors.isEnabled(), gaps, segments,
                first == null ? null : new Endpoint(first.at(), first.lat(), first.lng()),
                last == null ? null : new Endpoint(last.at(), last.lat(), last.lng()));
    }

    /** Portion routiere ; repli sur la ligne droite brute (RAW) si ORS est indisponible, en echec ou hors quota. */
    private Segment roadSegment(List<P> chunk, Kind kind, int[] calls) {
        Optional<List<double[]>> road = route(chunk, calls);
        if (road.isPresent()) {
            return new Segment(kind, road.get());
        }
        return new Segment(Kind.RAW, chunk.stream().map(p -> new double[]{p.lat(), p.lng()}).toList());
    }

    private Optional<List<double[]>> route(List<P> chunk, int[] calls) {
        if (!ors.isEnabled()) {
            return Optional.empty();
        }
        StringBuilder key = new StringBuilder();
        List<double[]> waypoints = new ArrayList<>();
        for (P p : chunk) {
            waypoints.add(new double[]{p.lat(), p.lng()});
            key.append(String.format(java.util.Locale.ROOT, "%.5f,%.5f;", p.lat(), p.lng()));
        }
        List<double[]> cached = cache.get(key.toString());
        if (cached != null) {
            return Optional.of(cached);
        }
        if (calls[0] >= MAX_CALLS) {
            return Optional.empty();
        }
        calls[0]++;
        Optional<List<double[]>> road = ors.routeThrough(waypoints);
        road.ifPresent(geometry -> {
            if (cache.size() >= CACHE_LIMIT) {
                cache.clear();
            }
            cache.put(key.toString(), geometry);
        });
        return road;
    }

    /** Ne garde qu'un point tous les MIN_STEP_METERS ; le premier et le dernier sont toujours conserves. */
    private static List<P> thin(List<P> points) {
        List<P> kept = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            P p = points.get(i);
            if (kept.isEmpty() || i == points.size() - 1 || distanceMeters(kept.get(kept.size() - 1), p) >= MIN_STEP_METERS) {
                kept.add(p);
            }
        }
        return kept;
    }

    private static double distanceMeters(P a, P b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
    }
}
