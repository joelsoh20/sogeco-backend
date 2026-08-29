package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.common.enums.WebhookStatus;
import com.sogeco.fleet.common.event.PositionReceivedEvent;
import com.sogeco.fleet.common.util.GeoUtils;
import com.sogeco.fleet.modules.alert.AlertEngine;
import com.sogeco.fleet.modules.alert.evaluator.EvaluationContext;
import com.sogeco.fleet.modules.geofence.GeofenceEvaluationService;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.mission.MissionService;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.dto.LivePosition;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleAssignment;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentRepository;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Traitement des trames telematiques.
 *
 * Execute en asynchrone : le webhook accuse reception en moins de
 * 200 ms, tout le metier se fait ici. Un traitement lent provoquerait
 * des rejeux cote Traccar et une saturation en cascade.
 *
 * Chaine : validation, deduplication, distance incrementale, ecriture,
 * georeperage, moteur d'alertes, diffusion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelematicsIngestionService {

    private final GpsPositionRepository positionRepository;
    private final VehicleDiagnosticRepository diagnosticRepository;
    private final WebhookEventRepository webhookRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final MissionRepository missionRepository;
    private final MissionService missionService;
    private final PositionCacheService cache;
    private final AlertEngine alertEngine;
    private final GeofenceEvaluationService geofenceService;
    private final SettingService settingService;
    private final ApplicationEventPublisher events;

    @Async("telematicsExecutor")
    @Transactional
    public void process(Long webhookEventId, TelematicsPayload payload) {
        WebhookEvent event = webhookRepository.findById(webhookEventId).orElse(null);

        try {
            Optional<Vehicle> found = vehicleRepository.findByDeviceId(payload.deviceId());
            if (found.isEmpty()) {
                reject(event, WebhookStatus.APPAREIL_INCONNU,
                        "Aucun camion ne porte le boitier " + payload.deviceId());
                return;
            }

            Vehicle vehicle = found.get();

            String rejection = validate(payload);
            if (rejection != null) {
                reject(event, WebhookStatus.REJETE, rejection);
                return;
            }

            // Doublon : reseaux mobiles et rejeux de Traccar en produisent.
            if (positionRepository.existsByVehicleIdAndRecordedAt(vehicle.getId(), payload.recordedAt())) {
                reject(event, WebhookStatus.DOUBLON, "Position deja enregistree");
                return;
            }

            LivePosition previous = cache.find(vehicle.getId()).orElse(null);
            Mission activeMission = findActiveMission(vehicle.getId());

            BigDecimal distance = computeDistance(previous, payload, vehicle);

            GpsPosition position = positionRepository.save(GpsPosition.builder()
                    .vehicleId(vehicle.getId())
                    .missionId(activeMission == null ? null : activeMission.getId())
                    .recordedAt(payload.recordedAt())
                    .latitude(payload.latitude())
                    .longitude(payload.longitude())
                    .speedKmh(payload.speedKmh())
                    .heading(payload.heading())
                    .altitude(payload.altitude())
                    .ignitionOn(payload.ignitionOn())
                    .odometerKm(payload.odometerKm())
                    .fuelLevelPercent(payload.fuelLevelPercent())
                    .fuelLevelLiters(payload.fuelLevelLiters())
                    .distanceKm(distance)
                    .protocol(payload.protocol())
                    .valid(payload.valid() == null || payload.valid())
                    .build());

            updateVehicle(vehicle, payload, distance, previous == null);

            if (payload.hasDiagnostics()) {
                storeDiagnostics(vehicle, payload);
            }

            if (activeMission != null && distance != null) {
                advanceMission(activeMission, distance);
            }

            geofenceService.evaluate(vehicle, payload.latitude(), payload.longitude(), payload.recordedAt());

            alertEngine.evaluate(payload, vehicle,
                    new EvaluationContext(previous, activeMission));

            LivePosition live = buildLivePosition(vehicle, payload, activeMission);
            cache.store(live);
            broadcast(vehicle, live);

            if (event != null) {
                event.markProcessed();
            }

        } catch (RuntimeException e) {
            log.error("Traitement de la trame {} en echec", webhookEventId, e);
            reject(event, WebhookStatus.REJETE, e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Controles de vraisemblance
    // ------------------------------------------------------------------

    /**
     * Une trame invraisemblable est rejetee plutot que stockee : une
     * seule position aberrante fausserait durablement le kilometrage.
     */
    private String validate(TelematicsPayload payload) {

        if (!payload.isValidPosition()) {
            return "Position marquee invalide par le boitier";
        }

        if (!GeoUtils.isValidCoordinate(payload.latitude().doubleValue(),
                                        payload.longitude().doubleValue())) {
            return "Coordonnees hors bornes : %s, %s".formatted(payload.latitude(), payload.longitude());
        }

        int skewMinutes = settingService.getInt("telematics.max_clock_skew_min", 10);
        if (payload.recordedAt().isAfter(Instant.now().plusSeconds(skewMinutes * 60L))) {
            return "Horodatage dans le futur : " + payload.recordedAt();
        }

        int maxSpeed = settingService.getInt("telematics.max_speed_kmh", 160);
        if (payload.speedKmh() != null
                && payload.speedKmh().compareTo(BigDecimal.valueOf(maxSpeed)) > 0) {
            return "Vitesse invraisemblable : %s km/h".formatted(payload.speedKmh());
        }

        return null;
    }

    /**
     * Distance depuis la position precedente.
     *
     * L'odometre du boitier fait autorite quand il est disponible et
     * vraisemblable : mesure directe (roue/CAN), il suit
     * la route reelle la ou Haversine trace une ligne droite entre deux
     * points — au risque de couper les virages si l'echantillonnage est
     * espace. A defaut d'odometre, repli sur Haversine.
     *
     * Dans les deux cas, un saut impossible — plus de 200 km/h implicites
     * — est ecarte : c'est le cas typique d'une position GPS aberrante
     * captee sous un pont ou en zone urbaine dense, ou d'un odometre qui
     * vient de deraper (remise a zero, changement d'unite du boitier).
     */
    private BigDecimal computeDistance(LivePosition previous, TelematicsPayload payload, Vehicle vehicle) {
        if (previous == null || previous.latitude() == null) {
            return BigDecimal.ZERO;
        }

        long seconds = Duration.between(previous.recordedAt(), payload.recordedAt()).getSeconds();
        if (seconds <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal odometer = odometerDistance(vehicle, payload, seconds);
        if (odometer != null) {
            return odometer;
        }

        double implied = GeoUtils.impliedSpeedKmh(
                previous.latitude().doubleValue(), previous.longitude().doubleValue(),
                payload.latitude().doubleValue(), payload.longitude().doubleValue(),
                seconds);

        if (implied > 200) {
            log.warn("Saut geographique ecarte : {} km/h implicites entre deux trames", (int) implied);
            return BigDecimal.ZERO;
        }

        double km = GeoUtils.distanceKm(
                previous.latitude().doubleValue(), previous.longitude().doubleValue(),
                payload.latitude().doubleValue(), payload.longitude().doubleValue());

        return BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * Delta d'odometre depuis le dernier kilometrage connu du vehicule.
     * Retourne null (repli sur Haversine) si le boitier n'en remonte pas,
     * si le delta est negatif ou nul (odometre pas encore avance, ou
     * qui vient de reculer), ou si le delta implique plus de 200 km/h —
     * signe d'un odometre incoherent plutot que d'un trajet reel.
     */
    private BigDecimal odometerDistance(Vehicle vehicle, TelematicsPayload payload, long seconds) {
        if (payload.odometerKm() == null) {
            return null;
        }

        BigDecimal delta = payload.odometerKm().subtract(vehicle.getCurrentKilometers());
        if (delta.signum() <= 0) {
            return null;
        }

        double impliedSpeed = delta.doubleValue() / (seconds / 3600.0);
        if (impliedSpeed > 200) {
            log.warn("Delta d'odometre ecarte : {} km/h implicites, repli sur Haversine", (int) impliedSpeed);
            return null;
        }

        return delta.setScale(3, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Mises a jour
    // ------------------------------------------------------------------

    private void updateVehicle(Vehicle vehicle, TelematicsPayload payload, BigDecimal distance,
                               boolean firstPosition) {
        vehicle.addDistance(distance);

        // Toute premiere position connue du vehicule : aucun delta n'est
        // calculable faute de position precedente. L'odometre du boitier,
        // s'il est plus recent que le kilometrage enregistre, sert alors de
        // base de depart — sans quoi computeDistance() n'a plus jamais
        // d'ecart a mesurer et la derive ne serait plus jamais corrigee.
        if (firstPosition && payload.odometerKm() != null
                && payload.odometerKm().compareTo(vehicle.getCurrentKilometers()) > 0) {
            vehicle.setCurrentKilometers(payload.odometerKm());
        }

        // Sonde de reservoir uniquement : une valeur mesuree, jamais une
        // estimation. Sans sonde, FuelAnalyticsService.tankLevelFor() prend
        // le relais a la demande, a partir de la distance et des pleins —
        // dupliquer cette estimation ici melangerait mesure et estimation
        // sous le meme champ, sans plus moyen de les distinguer.
        if (payload.fuelLevelPercent() != null) {
            vehicle.setFuelLevelPercent(payload.fuelLevelPercent());
        }
        if (payload.fuelLevelLiters() != null) {
            vehicle.setFuelLevelLiters(payload.fuelLevelLiters());
        }
    }

    private void storeDiagnostics(Vehicle vehicle, TelematicsPayload payload) {
        List<String> codes = payload.errorCodes() == null ? List.of() : payload.errorCodes();

        diagnosticRepository.save(VehicleDiagnostic.builder()
                .vehicleId(vehicle.getId())
                .recordedAt(payload.recordedAt())
                .engineTemperature(payload.engineTemperature())
                .engineRpm(payload.engineRpm())
                .batteryVoltage(payload.batteryVoltage())
                .engineHours(payload.engineHours())
                .errorCodes(codes.isEmpty() ? null
                        : "[\"%s\"]".formatted(String.join("\",\"", codes)))
                .dtcCount(codes.size())
                .build());
    }

    /** L'avancement se calcule, il ne se saisit jamais (RG-5.4). */
    private void advanceMission(Mission mission, BigDecimal distance) {
        List<GpsPosition> positions = positionRepository.findByMissionIdOrderByRecordedAtAsc(mission.getId());
        BigDecimal traveled = positions.stream()
                .map(GpsPosition::getDistanceKm)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        missionService.updateProgress(mission.getId(), traveled);
    }

    private Mission findActiveMission(Long vehicleId) {
        return missionRepository.findActiveByVehicle(vehicleId).stream()
                .filter(mission -> mission.getStatus() == MissionStatus.EN_COURS)
                .findFirst()
                .orElse(null);
    }

    private LivePosition buildLivePosition(Vehicle vehicle, TelematicsPayload payload, Mission mission) {
        VehicleAssignment assignment = assignmentRepository
                .findByVehicleIdAndEndDateIsNull(vehicle.getId()).orElse(null);

        return new LivePosition(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getDeviceId(),
                assignment == null ? null : assignment.getDriver().getId(),
                assignment == null ? null : assignment.getDriver().getFullName(),
                mission == null ? null : mission.getId(),
                mission == null ? null : mission.getMissionNumber(),
                mission == null ? null : destinationLabel(mission),
                vehicle.getStatus(),
                payload.latitude(),
                payload.longitude(),
                payload.speedKmh(),
                payload.heading(),
                payload.ignitionOn(),
                vehicle.getFuelLevelPercent(),
                vehicle.getFuelLevelLiters(),
                vehicle.getDailyKm(),
                payload.recordedAt(),
                true);
    }

    private String destinationLabel(Mission mission) {
        return mission.getDestinationCity() != null
                ? mission.getDestinationCity().getName()
                : mission.getDestinationAddress();
    }

    /**
     * Diffusion WebSocket, a debit limite : sans cela, onze camions
     * emettant toutes les 30 secondes inonderaient les navigateurs.
     */
    private void broadcast(Vehicle vehicle, LivePosition live) {
        int minimum = settingService.getInt("telematics.broadcast_min_sec", 10);
        if (cache.canBroadcast(vehicle.getId(), minimum)) {
            events.publishEvent(new PositionReceivedEvent(live));
        }
    }

    private void reject(WebhookEvent event, WebhookStatus status, String message) {
        if (event != null) {
            event.markRejected(status, message);
        }
        log.debug("Trame rejetee [{}] : {}", status, message);
    }
}
