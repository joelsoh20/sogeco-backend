package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.common.util.GeoUtils;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.GpsPosition;
import com.sogeco.fleet.modules.tracking.GpsPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Arret prolonge en mission, hors des lieux prevus.
 *
 * Toutes les minutes : pour chaque mission EN_COURS dont le camion est immobile depuis plus de
 * N minutes (seuil de la regle, 15 par defaut), l'alerte est levee si le camion n'est pas a
 * moins de R metres (parametre alerts.stop_radius_meters, 50 par defaut) du point de depart ni
 * du point de livraison. Gestionnaire (de la ville) et administrateur la recoivent comme toute
 * alerte : cloche, ecran Alertes et notification push.
 *
 * Un camion hors ligne n'est pas juge (pas de signal != arret). L'alerte se ferme toute seule
 * quand le camion repart, arrive a un point prevu, ou que la mission se termine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnexpectedStopMonitor {

    private static final int DEFAULT_MINUTES = 15;
    private static final int DEFAULT_RADIUS_METERS = 50;

    private final MissionRepository missionRepository;
    private final com.sogeco.fleet.modules.mission.MissionWaypointRepository waypointRepository;
    private final GpsPositionRepository positionRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;
    private final SettingService settingService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void scheduled() {
        try {
            evaluate();
        } catch (RuntimeException e) {
            log.warn("Controle des arrets prolonges en echec : {}", e.getMessage());
        }
    }

    @Transactional
    public void evaluate() {
        AlertRule rule = ruleRepository.findByAlertTypeAndActiveTrue(AlertType.ARRET_NON_AUTORISE).orElse(null);
        if (rule == null) {
            return;
        }
        int minutes = rule.getThresholdValue() == null ? DEFAULT_MINUTES : rule.getThresholdValue().intValue();
        int radius = settingService.getInt("alerts.stop_radius_meters", DEFAULT_RADIUS_METERS);

        List<Mission> missions = missionRepository.findByStatusOrderByPlannedStartAsc(MissionStatus.EN_COURS);
        int offlineMinutes = settingService.getInt("gps.offline_threshold_minutes", 30);
        BigDecimal idleSpeed = BigDecimal.valueOf(settingService.getInt("telematics.idle_speed_kmh", 3));
        // Debut de la serie de releves a l'arret en cours, par camion (acces direct : tache planifiee, sans utilisateur).
        Map<Long, Instant> stopStarts = new HashMap<>();
        for (Object[] row : positionRepository.stoppedStreakStarts(Instant.now().minus(1, ChronoUnit.DAYS), idleSpeed)) {
            Object ts = row[1];
            stopStarts.put(((Number) row[0]).longValue(),
                    ts instanceof Timestamp t ? t.toInstant() : ts instanceof OffsetDateTime o ? o.toInstant() : (Instant) ts);
        }

        Set<Long> violating = new HashSet<>();
        Instant limit = Instant.now().minus(Duration.ofMinutes(minutes));

        for (Mission mission : missions) {
            Long vehicleId = mission.getVehicle().getId();
            Instant since = stopStarts.get(vehicleId);
            if (since == null || since.isAfter(limit)) {
                continue;
            }
            GpsPosition position = positionRepository.findLatest(vehicleId).orElse(null);
            if (position == null || !Boolean.TRUE.equals(position.getValid())
                    || position.getRecordedAt().isBefore(Instant.now().minus(offlineMinutes, ChronoUnit.MINUTES))
                    || (position.getSpeedKmh() != null && position.getSpeedKmh().compareTo(idleSpeed) > 0)) {
                continue; // hors ligne (pas de signal != arret) ou deja reparti
            }

            // Depart, arrivee, puis les points de livraison supplementaires de la tournee.
            List<BigDecimal[]> places = new java.util.ArrayList<>(List.of(
                    new BigDecimal[]{depLat(mission), depLng(mission)},
                    new BigDecimal[]{destLat(mission), destLng(mission)}));
            for (var waypoint : waypointRepository.findByMissionIdOrderBySequenceNumberAsc(mission.getId())) {
                places.add(new BigDecimal[]{waypoint.getLatitude(), waypoint.getLongitude()});
            }
            boolean known = false;
            double nearest = Double.MAX_VALUE;
            for (BigDecimal[] place : places) {
                if (place[0] == null || place[1] == null) {
                    continue;
                }
                known = true;
                nearest = Math.min(nearest, GeoUtils.distanceKm(
                        position.getLatitude(), position.getLongitude(), place[0], place[1]) * 1000.0);
            }
            // Aucun point de la mission n'est localise : impossible de dire que le lieu est interdit.
            if (!known || nearest <= radius) {
                continue;
            }

            violating.add(vehicleId);
            long stoppedMinutes = Duration.between(since, Instant.now()).toMinutes();
            String where = nearest >= 1000
                    ? "%.1f km".formatted(nearest / 1000.0)
                    : "%d m".formatted(Math.round(nearest));
            String text = "%s à l'arrêt depuis %d min (mission %s, %s), à %s du point de départ ou de livraison le plus proche (limite %d m)."
                    .formatted(mission.getVehicle().getRegistrationNumber(), stoppedMinutes,
                            mission.getMissionNumber(),
                            mission.getDriver() == null ? "chauffeur inconnu" : mission.getDriver().getFullName(),
                            where, radius);

            alertService.raise(AlertService.AlertRequest.builder()
                    .type(AlertType.ARRET_NON_AUTORISE)
                    .level(rule.getLevel() == null ? AlertLevel.CRITIQUE : rule.getLevel())
                    .rule(rule)
                    .vehicle(mission.getVehicle())
                    .driver(mission.getDriver())
                    .mission(mission)
                    .latitude(position.getLatitude())
                    .longitude(position.getLongitude())
                    .title("Arrêt prolongé hors lieu prévu — " + mission.getVehicle().getRegistrationNumber())
                    .description(text)
                    .refreshDescription(true)
                    .build());
        }

        for (Alert alert : alertRepository.findAllOpen(AlertType.ARRET_NON_AUTORISE)) {
            if (alert.getVehicle() == null || !violating.contains(alert.getVehicle().getId())) {
                alertService.autoResolve(alert, "Camion reparti, arrive a un point prevu, ou mission terminee");
            }
        }
    }

    private static BigDecimal depLat(Mission m) {
        return m.getDepartureLatitude() != null ? m.getDepartureLatitude()
                : m.getAgency() == null ? null : m.getAgency().getLatitude();
    }

    private static BigDecimal depLng(Mission m) {
        return m.getDepartureLongitude() != null ? m.getDepartureLongitude()
                : m.getAgency() == null ? null : m.getAgency().getLongitude();
    }

    private static BigDecimal destLat(Mission m) {
        return m.getDestinationLatitude() != null ? m.getDestinationLatitude()
                : m.getDestinationAgency() == null ? null : m.getDestinationAgency().getLatitude();
    }

    private static BigDecimal destLng(Mission m) {
        return m.getDestinationLongitude() != null ? m.getDestinationLongitude()
                : m.getDestinationAgency() == null ? null : m.getDestinationAgency().getLongitude();
    }
}
