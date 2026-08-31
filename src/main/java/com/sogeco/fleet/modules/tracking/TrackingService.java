package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.fuel.FuelAnalyticsService;
import com.sogeco.fleet.modules.fuel.dto.TankLevelResponse;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.dto.LivePosition;
import com.sogeco.fleet.modules.tracking.dto.TrackHistoryResponse;
import com.sogeco.fleet.modules.tracking.dto.TrackingStatsResponse;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleAssignment;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentRepository;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lecture des donnees de suivi.
 *
 * La carte lit le cache Redis, jamais gps_positions : interroger une
 * table de plus d'un million de lignes pour afficher onze marqueurs
 * serait absurde. La base n'est sollicitee qu'a froid, quand le cache
 * est vide.
 */
@Service
@RequiredArgsConstructor
public class TrackingService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private final PositionCacheService cache;
    private final GpsPositionRepository positionRepository;
    private final VehicleDiagnosticRepository diagnosticRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final MissionRepository missionRepository;
    private final FuelAnalyticsService fuelAnalyticsService;
    private final SettingService settingService;

    /**
     * Une position par camion actif : le cache Redis fait autorite quand il
     * en a une, sinon la derniere position connue en base (a froid), sinon
     * — camion sans boitier ou jamais encore capte — la position donnee
     * par sa mission en cours, moins fiable mais preferable a une absence
     * totale du camion sur la carte.
     *
     * Exception volontaire au cloisonnement par ville (RG-13.4) : la carte
     * temps reel montre tout le parc a tout le monde, y compris un
     * gestionnaire d'une ville precise — utile pour suivre un camion qui
     * entre ou sort de sa zone. Le cloisonnement reste actif ailleurs
     * (Carburant, Maintenance, Alertes, Audit...).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_READ')")
    public List<LivePosition> currentPositions() {
        Map<Long, LivePosition> cached = cache.findAll().stream()
                .collect(Collectors.toMap(LivePosition::vehicleId, Function.identity(), (a, b) -> a));
        Map<Long, VehicleAssignment> assignments = activeAssignmentsByVehicle();

        return vehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc().stream()
                .map(vehicle -> resolvePosition(vehicle, cached.get(vehicle.getId()), assignments.get(vehicle.getId())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_READ')")
    public Optional<LivePosition> currentPosition(Long vehicleId) {
        Vehicle vehicle = findVehicle(vehicleId);
        VehicleAssignment assignment = assignmentRepository.findByVehicleIdAndEndDateIsNull(vehicleId).orElse(null);
        return Optional.ofNullable(resolvePosition(vehicle, cache.find(vehicleId).orElse(null), assignment));
    }

    private Map<Long, VehicleAssignment> activeAssignmentsByVehicle() {
        Map<Long, VehicleAssignment> map = new HashMap<>();
        assignmentRepository.findByEndDateIsNull()
                .forEach(assignment -> map.put(assignment.getVehicle().getId(), assignment));
        return map;
    }

    /**
     * Le chauffeur affecte et le niveau de carburant sont deja corrects
     * dans une position issue du cache ou de la telematique (voir
     * TelematicsIngestionService.buildLivePosition) : seuls les repères
     * de repli batis ici (base a froid, mission, ville) en avaient
     * besoin explicitement.
     */
    private LivePosition resolvePosition(Vehicle vehicle, LivePosition cached, VehicleAssignment assignment) {
        if (cached != null) {
            return cached;
        }
        LivePosition fromDatabase = rebuildFromDatabase(vehicle, assignment);
        if (fromDatabase != null) {
            return fromDatabase;
        }
        LivePosition fromActiveMission = missionFallbackPosition(vehicle, assignment);
        if (fromActiveMission != null) {
            return fromActiveMission;
        }
        return lastKnownPosition(vehicle, assignment);
    }

    private Long driverId(VehicleAssignment assignment) {
        return assignment == null ? null : assignment.getDriver().getId();
    }

    private String driverName(VehicleAssignment assignment) {
        return assignment == null ? null : assignment.getDriver().getFullName();
    }

    /**
     * Sonde reelle si le camion en a une, sinon la meme estimation
     * "distance depuis le premier plein" que l'ecran Carburant
     * (FuelAnalyticsService.tankLevelFor) — jamais de sonde inventee,
     * mais jamais une carte plus pauvre que l'ecran Carburant non plus.
     */
    private TankLevelResponse fuelLevel(Vehicle vehicle) {
        if (vehicle.getFuelLevelPercent() != null) {
            return new TankLevelResponse(vehicle.getId(), vehicle.getRegistrationNumber(),
                    vehicle.getTankCapacityLiters(), vehicle.getFuelLevelLiters(), vehicle.getFuelLevelPercent(),
                    null, null, TankLevelResponse.TankLevelSource.TELEMATIQUE);
        }
        return fuelAnalyticsService.tankLevelFor(vehicle);
    }

    /**
     * Vehicle.dailyKm n'est incremente que par la telematique (voir
     * TelematicsIngestionService.addDistance) : pour un camion sans boitier,
     * il reste bloque a zero meme apres une mission terminee dans la
     * journee. On retombe alors sur la distance des missions cloturees
     * aujourd'hui pour ce camion, seule source fiable disponible sans GPS.
     */
    private BigDecimal dailyKmFor(Vehicle vehicle) {
        if (vehicle.getDailyKm() != null && vehicle.getDailyKm().signum() > 0) {
            return vehicle.getDailyKm();
        }
        LocalDate today = LocalDate.now(ZONE);
        Instant start = today.atStartOfDay(ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        return missionRepository.totalDistanceForVehicle(vehicle.getId(), start, end);
    }

    /**
     * Camion sans historique GPS (pas de boitier, ou jamais encore capte) :
     * on affiche la position de depart de sa mission en cours, a defaut
     * de connaitre sa position reelle. gpsTracked=false signale a la carte
     * qu'il s'agit d'une approximation, pas d'une position mesuree.
     */
    private LivePosition missionFallbackPosition(Vehicle vehicle, VehicleAssignment assignment) {
        Mission mission = missionRepository.findActiveByVehicle(vehicle.getId()).stream()
                .filter(m -> m.getStatus() == MissionStatus.EN_COURS || m.getStatus() == MissionStatus.EN_ATTENTE)
                .findFirst()
                .orElse(null);
        if (mission == null) {
            return null;
        }

        BigDecimal latitude = mission.getDepartureLatitude() != null
                ? mission.getDepartureLatitude()
                : mission.getOriginCity() == null ? null : mission.getOriginCity().getLatitude();
        BigDecimal longitude = mission.getDepartureLongitude() != null
                ? mission.getDepartureLongitude()
                : mission.getOriginCity() == null ? null : mission.getOriginCity().getLongitude();
        if (latitude == null || longitude == null) {
            return null;
        }

        String destination = mission.getDestinationCity() != null
                ? mission.getDestinationCity().getName()
                : mission.getDestinationAddress();

        TankLevelResponse fuel = fuelLevel(vehicle);
        return new LivePosition(
                vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getDeviceId(),
                driverId(assignment), driverName(assignment), mission.getId(), mission.getMissionNumber(), destination,
                vehicle.getStatus(), latitude, longitude,
                null, null, null,
                fuel.estimatedFuelPercent(), fuel.estimatedFuelLiters(), dailyKmFor(vehicle),
                mission.getCreatedAt(), false);
    }

    /**
     * Dernier recours : camion sans boitier ET sans mission en cours en ce
     * moment. On approxime avec le lieu de depart de sa toute derniere
     * mission, quel que soit son statut — une mission terminee ramene le
     * camion a son point de depart, pas a la destination livree ; a
     * defaut de toute mission, sa ville de rattachement. gpsTracked=false
     * dans les deux cas : ce n'est jamais une position mesuree.
     */
    private LivePosition lastKnownPosition(Vehicle vehicle, VehicleAssignment assignment) {
        Mission mission = missionRepository.findFirstByVehicleIdOrderByCreatedAtDesc(vehicle.getId()).orElse(null);
        if (mission != null) {
            LivePosition fromMission = missionEndpointPosition(vehicle, mission, assignment);
            if (fromMission != null) {
                return fromMission;
            }
        }
        return homeCityPosition(vehicle, assignment);
    }

    private LivePosition missionEndpointPosition(Vehicle vehicle, Mission mission, VehicleAssignment assignment) {
        GeoPoint point = geoPoint(mission.getDepartureLatitude(), mission.getDepartureLongitude(),
                mission.getAgency(), mission.getOriginCity());
        if (point == null) {
            return null;
        }

        Instant recordedAt = mission.getActualEnd() != null ? mission.getActualEnd() : mission.getCreatedAt();

        TankLevelResponse fuel = fuelLevel(vehicle);
        return new LivePosition(
                vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getDeviceId(),
                driverId(assignment), driverName(assignment), mission.getId(), mission.getMissionNumber(), mission.originLabel(),
                vehicle.getStatus(), point.latitude(), point.longitude(),
                null, null, null,
                fuel.estimatedFuelPercent(), fuel.estimatedFuelLiters(), dailyKmFor(vehicle),
                recordedAt, false);
    }

    private LivePosition homeCityPosition(Vehicle vehicle, VehicleAssignment assignment) {
        City city = vehicle.getCity();
        if (city == null || city.getLatitude() == null || city.getLongitude() == null) {
            return null;
        }
        TankLevelResponse fuel = fuelLevel(vehicle);
        return new LivePosition(
                vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getDeviceId(),
                driverId(assignment), driverName(assignment), null, null, city.getName(),
                vehicle.getStatus(), city.getLatitude(), city.getLongitude(),
                null, null, null,
                fuel.estimatedFuelPercent(), fuel.estimatedFuelLiters(), dailyKmFor(vehicle),
                vehicle.getCreatedAt(), false);
    }

    private record GeoPoint(BigDecimal latitude, BigDecimal longitude) {
    }

    /** Priorite : coordonnees explicites de la mission, puis site, puis ville. */
    private GeoPoint geoPoint(BigDecimal latitude, BigDecimal longitude, Agency agency, City city) {
        if (latitude != null && longitude != null) {
            return new GeoPoint(latitude, longitude);
        }
        if (agency != null && agency.getLatitude() != null && agency.getLongitude() != null) {
            return new GeoPoint(agency.getLatitude(), agency.getLongitude());
        }
        if (city != null && city.getLatitude() != null && city.getLongitude() != null) {
            return new GeoPoint(city.getLatitude(), city.getLongitude());
        }
        return null;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_HISTORY_READ')")
    public TrackHistoryResponse history(Long vehicleId, Instant from, Instant to) {
        return TrackHistoryResponse.of(vehicleId, from, to,
                positionRepository.findHistory(vehicleId, from, to));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_READ')")
    public Optional<VehicleDiagnostic> latestDiagnostic(Long vehicleId) {
        return diagnosticRepository.findLatest(vehicleId);
    }

    /** Legende de la carte : compteurs par etat. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('TRACKING_READ')")
    public TrackingStatsResponse stats() {
        int offlineMinutes = settingService.getInt("gps.offline_threshold_minutes", 30);
        BigDecimal idleThreshold = BigDecimal.valueOf(
                settingService.getInt("telematics.idle_speed_kmh", 3));

        Map<Long, LivePosition> positions = currentPositions().stream()
                .collect(Collectors.toMap(LivePosition::vehicleId, Function.identity(), (a, b) -> a));

        long moving = 0;
        long idle = 0;
        long offline = 0;
        long maintenance = 0;

        List<Vehicle> vehicles = vehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc();
        for (Vehicle vehicle : vehicles) {
            if (vehicle.getStatus() == VehicleStatus.EN_MAINTENANCE
                    || vehicle.getStatus() == VehicleStatus.EN_PANNE) {
                maintenance++;
                continue;
            }

            LivePosition position = positions.get(vehicle.getId());
            if (position == null || position.isOffline(offlineMinutes)) {
                offline++;
            } else if (position.isMovingAbove(idleThreshold)) {
                moving++;
            } else {
                idle++;
            }
        }

        return new TrackingStatsResponse(moving, idle, offline, maintenance, vehicles.size());
    }

    // ------------------------------------------------------------------

    private LivePosition rebuildFromDatabase(Vehicle vehicle, VehicleAssignment assignment) {
        return positionRepository.findLatest(vehicle.getId())
                .map(position -> {
                    TankLevelResponse fuel = fuelLevel(vehicle);
                    return new LivePosition(
                        vehicle.getId(),
                        vehicle.getRegistrationNumber(),
                        vehicle.getDeviceId(),
                        driverId(assignment), driverName(assignment), position.getMissionId(), null, null,
                        vehicle.getStatus(),
                        position.getLatitude(), position.getLongitude(),
                        position.getSpeedKmh(), position.getHeading(),
                        position.getIgnitionOn(),
                        fuel.estimatedFuelPercent(), fuel.estimatedFuelLiters(),
                        vehicle.getDailyKm(), position.getRecordedAt(), true);
                })
                .orElse(null);
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", id));
    }
}
