package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.common.enums.WebhookStatus;
import com.sogeco.fleet.common.event.PositionReceivedEvent;
import com.sogeco.fleet.common.util.GeoUtils;
import com.sogeco.fleet.modules.alert.AlertEngine;
import com.sogeco.fleet.modules.alert.AlertService;
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
    private final AlertService alertService;
    private final GeofenceEvaluationService geofenceService;
    private final SettingService settingService;
    private final ApplicationEventPublisher events;

    @Async("telematicsExecutor")
    @Transactional
    public void process(Long webhookEventId, TelematicsPayload payload) {
        WebhookEvent event = webhookRepository.findById(webhookEventId).orElse(null);

        try {
            // Verrou pessimiste : serialise le traitement de deux trames du meme
            // camion arrivees en meme temps, cf. VehicleRepository.findByDeviceIdForUpdate.
            Optional<Vehicle> found = vehicleRepository.findByDeviceIdForUpdate(payload.deviceId());
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

            DistanceResult distanceResult = computeDistance(previous, payload, vehicle);
            BigDecimal distance = distanceResult.deltaKm();
            BigDecimal speedKmh = normalizeSpeed(payload.speedKmh());

            GpsPosition position = positionRepository.save(GpsPosition.builder()
                    .vehicleId(vehicle.getId())
                    .missionId(activeMission == null ? null : activeMission.getId())
                    .recordedAt(payload.recordedAt())
                    .latitude(payload.latitude())
                    .longitude(payload.longitude())
                    .speedKmh(speedKmh)
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

            updateVehicle(vehicle, payload, distanceResult);

            if (payload.hasDiagnostics()) {
                storeDiagnostics(vehicle, payload);
            }

            if (activeMission != null && distance != null) {
                advanceMission(activeMission, distance);
            }

            geofenceService.evaluate(vehicle, payload.latitude(), payload.longitude(), payload.recordedAt());

            alertEngine.evaluate(payload, vehicle,
                    new EvaluationContext(previous, activeMission));

            // Cette trame prouve que le signal est revenu : toute alerte de perte de
            // signal encore ouverte pour ce camion n'a plus lieu d'etre.
            alertService.resolveSignalRestored(vehicle.getId());

            LivePosition live = buildLivePosition(vehicle, payload, activeMission, speedKmh);
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
     * En dessous du seuil "a l'arret" (telematics.idle_speed_kmh), la vitesse
     * remontee par un boitier a l'arret n'est jamais tout a fait nulle — bruit
     * de mesure GPS, pas un mouvement reel. Sans ce plancher, le statut
     * afficherait "A l'arret" tandis que la vitesse affichee dirait "1 km/h" :
     * deux vues incoherentes du meme seuil, deja utilise par ailleurs
     * (legende de la carte, stats journalieres — TrackingService.stats(),
     * TelematicsScheduler).
     */
    private BigDecimal normalizeSpeed(BigDecimal speedKmh) {
        if (speedKmh == null) {
            return null;
        }
        int idleThreshold = settingService.getInt("telematics.idle_speed_kmh", 3);
        return speedKmh.compareTo(BigDecimal.valueOf(idleThreshold)) <= 0 ? BigDecimal.ZERO : speedKmh;
    }

    /**
     * Distance calculee pour une trame, et — si elle vient de l'odometre
     * du boitier — le kilometrage absolu auquel resynchroniser le camion.
     * odometerSyncKm reste nul pour une distance Haversine (accumulation
     * relative uniquement, cf. Vehicle.addDistance).
     */
    private record DistanceResult(BigDecimal deltaKm, BigDecimal odometerSyncKm) {
        static DistanceResult zero() {
            return new DistanceResult(BigDecimal.ZERO, null);
        }
    }

    /**
     * Distance depuis la position precedente.
     *
     * L'odometre du boitier fait autorite quand il est disponible et
     * vraisemblable : mesure directe (roue/CAN), il suit la route
     * reelle la ou Haversine trace une ligne droite entre deux points —
     * au risque de couper les virages si l'echantillonnage est espace.
     * Verifie AVANT toute chose (meme sans position precedente en
     * cache) : c'est ce qui permet au suivi de rattraper automatiquement
     * un retard pris sur le boitier (panne d'ingestion passee, cache
     * Redis expire) des que celui-ci revient a jour, plutot que de
     * necessiter une correction manuelle. A defaut d'odometre
     * exploitable, repli sur Haversine.
     *
     * Dans les deux cas, un saut impossible — plus de 200 km/h implicites
     * — est ecarte : c'est le cas typique d'une position GPS aberrante
     * captee sous un pont ou en zone urbaine dense, ou d'un odometre qui
     * vient de deraper (remise a zero, changement d'unite du boitier).
     *
     * Cote Haversine, un plancher de mouvement (gps.min_movement_meters)
     * ecarte aussi les sauts trop petits pour etre un vrai deplacement :
     * un camion a l'arret rapporte des positions legerement differentes
     * d'une trame a l'autre (precision du boitier), qui s'additionneraient
     * sinon en kilometrage fantome au fil de la journee.
     */
    private DistanceResult computeDistance(LivePosition previous, TelematicsPayload payload, Vehicle vehicle) {
        DistanceResult odometer = odometerDistance(vehicle, payload);
        if (odometer != null) {
            return odometer;
        }

        if (previous == null || previous.latitude() == null) {
            return DistanceResult.zero();
        }

        long seconds = Duration.between(previous.recordedAt(), payload.recordedAt()).getSeconds();
        if (seconds <= 0) {
            return DistanceResult.zero();
        }

        double implied = GeoUtils.impliedSpeedKmh(
                previous.latitude().doubleValue(), previous.longitude().doubleValue(),
                payload.latitude().doubleValue(), payload.longitude().doubleValue(),
                seconds);

        if (implied > 200) {
            log.warn("Saut geographique ecarte : {} km/h implicites entre deux trames", (int) implied);
            return DistanceResult.zero();
        }

        double km = GeoUtils.distanceKm(
                previous.latitude().doubleValue(), previous.longitude().doubleValue(),
                payload.latitude().doubleValue(), payload.longitude().doubleValue());

        // Camion a l'arret : le GPS "derive" naturellement de quelques metres
        // d'une trame a l'autre (precision du boitier), sans deplacement reel.
        // Sans ce plancher, ce bruit s'additionne au fil de la journee en un
        // kilometrage fantome — signale par un utilisateur dont le camion,
        // immobile depuis le matin, affichait tout de meme "1 km" parcouru.
        int minMovementMeters = settingService.getInt("gps.min_movement_meters", 20);
        if (km * 1000 < minMovementMeters) {
            return DistanceResult.zero();
        }

        return new DistanceResult(BigDecimal.valueOf(km).setScale(3, RoundingMode.HALF_UP), null);
    }

    /**
     * Delta d'odometre depuis le dernier releve CONNU DU BOITIER (table
     * gps_positions), pas depuis le kilometrage interne du camion : ce
     * dernier peut avoir pris du retard sur le boitier si des trames ont
     * ete perdues (panne Redis passee, conflit de verrou desormais
     * corrige, webhook mal configure) — comparer au boitier permet de
     * rattraper cet ecart des qu'il revient a jour, au lieu d'y rester
     * bloque indefiniment (l'ecart ne peut jamais se resorber tout seul
     * en ne comparant qu'au kilometrage interne, deja en retard : le
     * DistanceResult renvoye porte alors le releve absolu du boitier,
     * pour que updateVehicle() resynchronise le camion dessus plutot
     * que d'empiler un delta sur une base qui reste fausse).
     *
     * Repli sur le kilometrage interne uniquement pour le tout premier
     * releve d'odometre jamais recu de ce camion (aucune reference du
     * boitier a comparer) — accepte alors sans controle de vraisemblance
     * temporel, faute de point de comparaison.
     *
     * Retourne null (repli sur Haversine) si le boitier n'en remonte pas,
     * si le delta est negatif ou nul (odometre pas encore avance, ou
     * qui vient de reculer), ou si le delta implique plus de 200 km/h —
     * signe d'un odometre incoherent (remise a zero, changement d'unite)
     * plutot qu'un vrai trajet ou un vrai rattrapage.
     */
    private DistanceResult odometerDistance(Vehicle vehicle, TelematicsPayload payload) {
        if (payload.odometerKm() == null) {
            return null;
        }

        Optional<GpsPosition> reference = positionRepository.findLatestWithOdometer(vehicle.getId());

        if (reference.isEmpty()) {
            BigDecimal delta = payload.odometerKm().subtract(vehicle.getCurrentKilometers());
            return delta.signum() > 0
                    ? new DistanceResult(delta.setScale(3, RoundingMode.HALF_UP), payload.odometerKm())
                    : null;
        }

        GpsPosition previousOdometerReading = reference.get();
        BigDecimal delta = payload.odometerKm().subtract(previousOdometerReading.getOdometerKm());
        if (delta.signum() <= 0) {
            return null;
        }

        long seconds = Duration.between(previousOdometerReading.getRecordedAt(), payload.recordedAt()).getSeconds();
        if (seconds <= 0) {
            return null;
        }

        double impliedSpeed = delta.doubleValue() / (seconds / 3600.0);
        if (impliedSpeed > 200) {
            log.warn("Delta d'odometre ecarte : {} km/h implicites, repli sur Haversine", (int) impliedSpeed);
            return null;
        }

        return new DistanceResult(delta.setScale(3, RoundingMode.HALF_UP), payload.odometerKm());
    }

    // ------------------------------------------------------------------
    // Mises a jour
    // ------------------------------------------------------------------

    private void updateVehicle(Vehicle vehicle, TelematicsPayload payload, DistanceResult distanceResult) {
        if (distanceResult.odometerSyncKm() != null) {
            // Resynchronisation directe sur le releve du boitier, plutot qu'une
            // accumulation relative : rattrape immediatement un retard pris sur
            // le boitier (trames perdues avant correction), au lieu d'empiler
            // un delta sur une base qui resterait fausse indefiniment.
            vehicle.syncOdometer(distanceResult.odometerSyncKm(), distanceResult.deltaKm());
        } else {
            vehicle.addDistance(distanceResult.deltaKm());
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

    private LivePosition buildLivePosition(Vehicle vehicle, TelematicsPayload payload, Mission mission,
                                           BigDecimal speedKmh) {
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
                speedKmh,
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
