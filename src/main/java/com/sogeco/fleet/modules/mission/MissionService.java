package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.*;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.common.util.CoordinateParser;
import com.sogeco.fleet.common.util.GeoUtils;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.agency.AgencyRepository;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.client.ClientRepository;
import com.sogeco.fleet.modules.client.ServiceType;
import com.sogeco.fleet.modules.client.ServiceTypeRepository;
import com.sogeco.fleet.modules.client.Tariff;
import com.sogeco.fleet.modules.client.TariffService;
import com.sogeco.fleet.modules.document.DocumentService;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.mission.dto.*;
import com.sogeco.fleet.modules.quartier.Quartier;
import com.sogeco.fleet.modules.quartier.QuartierRepository;
import com.sogeco.fleet.modules.route.Route;
import com.sogeco.fleet.modules.route.RouteService;
import com.sogeco.fleet.modules.routing.GeocodingClient;
import com.sogeco.fleet.modules.routing.OpenRouteServiceClient;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import com.sogeco.fleet.modules.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.List;
import java.util.Optional;

/**
 * Cycle de vie des missions.
 *
 * Regles couvertes : RG-5.1 a RG-5.12 du cahier fonctionnel.
 *
 * La mission est l'unite d'analyse centrale : c'est ici que se joue la
 * fiabilite de tous les rapports. D'ou les controles stricts a la
 * creation et a la cloture.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MissionService {

    private static final String ENTITY = "Mission";

    private final MissionRepository repository;
    private final MissionWaypointRepository waypointRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final ClientRepository clientRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final AgencyRepository agencyRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;
    private final VehicleService vehicleService;
    private final RouteService routeService;
    private final OpenRouteServiceClient routingClient;
    private final GeocodingClient geocodingClient;
    private final QuartierRepository quartierRepository;
    private final TariffService tariffService;
    private final DocumentService documentService;
    private final SettingService settingService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public PageResponse<MissionResponse> list(Pageable pageable) {
        boolean financials = canSeeFinancials();

        // Un gestionnaire non-administrateur ne voit que les missions des
        // camions rattaches a sa ville.
        Page<Mission> page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));

        return PageResponse.from(page, mission -> MissionResponse.from(mission, financials));
    }

    /** Missions du chauffeur connecte, les plus recentes d'abord — espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public List<MissionResponse> myMissions() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED",
                        "Aucun dossier chauffeur associe a ce compte", HttpStatus.FORBIDDEN));
        return repository.findByDriverIdOrderByPlannedStartDesc(driver.getId()).stream()
                .map(mission -> MissionResponse.from(mission, false))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public MissionDetailResponse get(Long id) {
        Mission mission = repository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", id));

        return MissionDetailResponse.of(
                mission,
                settingService.getInt("mission.punctuality_margin_min", 60),
                waypointRepository.findByMissionIdOrderBySequenceNumberAsc(id).stream()
                        .map(WaypointResponse::from).toList(),
                documentService.listFor(EntityType.MISSION, id),
                canSeeFinancials());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public MissionStatsResponse stats(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        long total     = repository.countInPeriod(start, end);
        long terminees = repository.countByStatusInPeriod(MissionStatus.TERMINEE, start, end);
        long enCours   = repository.countByStatusInPeriod(MissionStatus.EN_COURS, start, end);
        long enAttente = repository.countByStatusInPeriod(MissionStatus.EN_ATTENTE, start, end);
        long annulees  = repository.countByStatusInPeriod(MissionStatus.ANNULEE, start, end);

        long missing = repository.findCompletedWithoutRevenue(Instant.now()).size();

        return new MissionStatsResponse(
                total, terminees, enCours, enAttente, annulees,
                percentage(terminees, total),
                percentage(annulees, total),
                canSeeFinancials() ? repository.totalRevenue(start, end) : null,
                repository.totalDistance(start, end),
                missing);
    }

    // ------------------------------------------------------------------
    // Creation et modification
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_CREATE')")
    public MissionDetailResponse create(MissionRequest request) {
        ServiceType serviceType = findServiceType(request.serviceTypeId());
        Vehicle vehicle = findVehicle(request.vehicleId());
        Driver driver = findDriver(request.driverId());

        // Une livraison facturable exige un client (RG-5.1)
        if (Boolean.TRUE.equals(serviceType.getBillable()) && request.clientId() == null) {
            throw new BusinessException("RG-5.1",
                    "Un client est obligatoire pour une prestation facturable",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        // Conformite du camion : assurance, visite technique, carte grise (RG-4.5)
        vehicleService.assertAssignable(request.vehicleId());

        // Etat du chauffeur (RG-9.3). Le permis expire n'est plus bloquant.
        if (!driver.isAssignable()) {
            throw new BusinessException("RG-9.3",
                    "Ce chauffeur n'est pas disponible : " + driver.getStatus(),
                    HttpStatus.CONFLICT);
        }

        assertNoOverlap(request.vehicleId(), request.driverId(),
                request.plannedStart(), request.plannedArrival(), null);

        Route route = findReferenceRoute(request.originCityId(), request.destinationCityId());

        // Distance initiale : le plus fiable disponible au moment de la creation
        // (corridor de reference, sinon estimation par coordonnees). Remplacee
        // par la distance reelle mesuree au GPS une fois la mission executee
        // (RG-5.10), ou saisie manuellement a la cloture.
        MissionEstimateResponse estimate = computeEstimate(
                request.originCityId(), request.destinationCityId(),
                request.agencyId(), request.destinationAgencyId(),
                request.originQuartierId(), request.destinationQuartierId(), request.vehicleId(),
                request.cargoWeightKg());

        Mission mission = Mission.builder()
                .missionNumber(nextMissionNumber())
                .client(request.clientId() == null ? null : clientRepository.findById(request.clientId())
                        .orElseThrow(() -> new ResourceNotFoundException("Client", request.clientId())))
                .serviceType(serviceType)
                .vehicle(vehicle)
                .driver(driver)
                .agency(request.agencyId() == null ? null
                        : agencyRepository.findById(request.agencyId()).orElse(null))
                .destinationAgency(request.destinationAgencyId() == null ? null
                        : agencyRepository.findById(request.destinationAgencyId()).orElse(null))
                .route(route)
                .originCity(resolveCity(request.originCityId()))
                .destinationCity(resolveCity(request.destinationCityId()))
                .departureAddress(request.departureAddress())
                .destinationAddress(request.destinationAddress())
                .plannedStart(request.plannedStart())
                .plannedArrival(request.plannedArrival())
                .cargoDescription(request.cargoDescription())
                .cargoWeightKg(request.cargoWeightKg())
                .cargoVolumeM3(request.cargoVolumeM3())
                .externalReference(request.externalReference())
                .missionFeeCost(request.missionFeeCost() == null ? BigDecimal.ZERO : request.missionFeeCost())
                .distanceKm(estimate.distanceKm())
                .status(MissionStatus.EN_ATTENTE)
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        // Le tarif est resolu des la creation : le montant propose est
        // ainsi connu du gestionnaire avant meme le depart.
        applyProposedRevenue(mission);

        Mission saved = repository.save(mission);
        log.info("Mission {} creee par {}", saved.getMissionNumber(), SecurityUtils.currentUserEmail());

        return get(saved.getId());
    }

    /**
     * Genere la mission du jour a partir d'un modele de livraison
     * recurrente (MissionAutomationScheduler, 9h30 chaque jour).
     *
     * Package-visible, pas de @PreAuthorize : appele uniquement par le
     * scheduler, sans utilisateur authentifie en contexte — meme choix
     * que LaverieScheduler pour le lavage hebdomadaire. Reutilise
     * computeEstimate/applyProposedRevenue pour rester coherente avec
     * une mission creee a la main.
     */
    @Transactional
    Mission generateAutomatedMission(MissionAutomation automation) {
        MissionEstimateResponse estimate = computeEstimate(
                automation.getCity().getId(), null,
                automation.getAgency().getId(), null,
                null, automation.getDestinationQuartier().getId(), automation.getVehicle().getId(),
                null);

        Mission mission = Mission.builder()
                .missionNumber(nextMissionNumber())
                .missionAutomation(automation)
                .client(automation.getClient())
                .serviceType(automation.getServiceType())
                .vehicle(automation.getVehicle())
                .driver(automation.getDriver())
                .agency(automation.getAgency())
                .originCity(automation.getCity())
                .destinationAddress(automation.getDestinationQuartier().getName())
                .plannedStart(Instant.now())
                .cargoDescription(automation.getCargoDescription())
                .distanceKm(estimate.distanceKm())
                .status(MissionStatus.EN_ATTENTE)
                .build();

        applyProposedRevenue(mission);

        Mission saved = repository.save(mission);
        log.info("Mission {} generee automatiquement depuis l'automatisation {} ({})",
                saved.getMissionNumber(), automation.getId(), automation.getLabel());
        return saved;
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public MissionDetailResponse update(Long id, MissionRequest request) {
        Mission mission = find(id);

        EditWindowGuard.assertEditable(mission.getCreatedAt(),
                settingService.getInt("mission.edit_window_hours", 1), "RG-5-EDIT", "Cette mission");

        if (mission.getStatus().isFinal()) {
            throw new BusinessException("RG-5.4",
                    "Une mission terminee ou annulee n'est plus modifiable", HttpStatus.CONFLICT);
        }

        assertNoOverlap(request.vehicleId(), request.driverId(),
                request.plannedStart(), request.plannedArrival(), id);

        mission.setVehicle(findVehicle(request.vehicleId()));
        mission.setDriver(findDriver(request.driverId()));
        mission.setOriginCity(resolveCity(request.originCityId()));
        mission.setDestinationCity(resolveCity(request.destinationCityId()));
        mission.setDestinationAgency(request.destinationAgencyId() == null ? null
                : agencyRepository.findById(request.destinationAgencyId()).orElse(null));
        mission.setRoute(findReferenceRoute(request.originCityId(), request.destinationCityId()));
        // Retirees du formulaire (ville/quartier structures les remplacent) : ne plus les
        // ecraser a null a chaque modification, pour ne pas effacer une valeur historique
        // saisie avant leur retrait.
        mission.setPlannedStart(request.plannedStart());
        mission.setPlannedArrival(request.plannedArrival());
        mission.setCargoDescription(request.cargoDescription());
        mission.setCargoWeightKg(request.cargoWeightKg());
        mission.setCargoVolumeM3(request.cargoVolumeM3());
        mission.setExternalReference(request.externalReference());
        if (request.missionFeeCost() != null) {
            mission.setMissionFeeCost(request.missionFeeCost());
        }

        return get(id);
    }

    // ------------------------------------------------------------------
    // Estimation (distance et carburant, avant execution)
    // ------------------------------------------------------------------

    private static final BigDecimal ROAD_DISTANCE_FACTOR = BigDecimal.valueOf(1.2);

    /**
     * Estime la distance et le carburant d'un trajet pas encore roule,
     * pour le formulaire de creation de mission.
     *
     * Quatre sources possibles, de la plus a la moins fiable :
     *  1. Corridor de reference (Route) : distance relevee sur des
     *     trajets reels — RIEN de plus fiable tant que le trajet lui-meme
     *     n'a pas ete parcouru avec un GPS actif.
     *  2. OpenRouteService (routage reel, profil poids lourd) entre les
     *     coordonnees de depart/arrivee : distance routiere effective,
     *     pas une approximation. Nécessite ORS_API_KEY configuree.
     *  3. A defaut (cle absente, appel en echec) : distance a vol
     *     d'oiseau entre les coordonnees, majoree de 20% pour approcher
     *     une distance routiere plausible. Sites (Agency) preferes aux
     *     villes car ce sont des points, pas des zones.
     *
     * Si une ville/un site n'a pas encore de coordonnees, resolveCoordinates
     * geocode automatiquement son nom (GeocodingClient) et les persiste —
     * d'ou une transaction non readOnly, contrairement aux autres consultations.
     */
    @Transactional
    @PreAuthorize("hasAuthority('MISSION_READ')")
    public MissionEstimateResponse estimate(Long originCityId, Long destinationCityId,
                                            Long originAgencyId, Long destinationAgencyId,
                                            Long originQuartierId, Long destinationQuartierId, Long vehicleId,
                                            BigDecimal cargoWeightKg) {
        return computeEstimate(originCityId, destinationCityId, originAgencyId, destinationAgencyId,
                originQuartierId, destinationQuartierId, vehicleId, cargoWeightKg);
    }

    private MissionEstimateResponse computeEstimate(Long originCityId, Long destinationCityId,
                                                     Long originAgencyId, Long destinationAgencyId,
                                                     Long originQuartierId, Long destinationQuartierId, Long vehicleId,
                                                     BigDecimal cargoWeightKg) {
        Route route = findReferenceRoute(originCityId, destinationCityId);
        if (route != null && route.getReferenceDistanceKm() != null) {
            return new MissionEstimateResponse(
                    route.getReferenceDistanceKm(), route.getReferenceFuelLiters(), "CORRIDOR_REFERENCE", null);
        }

        GeoPointPair points = resolveCoordinates(originCityId, destinationCityId, originAgencyId, destinationAgencyId,
                originQuartierId, destinationQuartierId);
        if (points == null) {
            return new MissionEstimateResponse(null, null, "INDISPONIBLE", null);
        }

        BigDecimal roadDistance;
        String source;
        List<double[]> geometry = null;

        Optional<OpenRouteServiceClient.RoadRoute> road = routingClient.route(
                BigDecimal.valueOf(points.originLat), BigDecimal.valueOf(points.originLng),
                BigDecimal.valueOf(points.destLat), BigDecimal.valueOf(points.destLng));

        if (road.isPresent()) {
            // Distance routiere reelle (reseau OpenStreetMap, profil poids lourd) : la
            // meilleure estimation disponible tant qu'aucun corridor n'est enregistre.
            roadDistance = road.get().distanceKm();
            source = "ROUTING_API";
            geometry = road.get().geometry();
        } else {
            // ORS non configure ou indisponible : repli sur la distance a vol d'oiseau majoree.
            double airKm = GeoUtils.distanceKm(points.originLat, points.originLng, points.destLat, points.destLng);
            roadDistance = BigDecimal.valueOf(airKm).multiply(ROAD_DISTANCE_FACTOR).setScale(2, RoundingMode.HALF_UP);
            source = points.source;
        }

        BigDecimal fuel = null;
        if (vehicleId != null) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
            BigDecimal consumption = vehicle == null ? null : resolveConsumption(vehicle, cargoWeightKg);
            if (consumption != null) {
                fuel = roadDistance.multiply(consumption)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            }
        }

        return new MissionEstimateResponse(roadDistance, fuel, source, geometry);
    }

    /**
     * Moyenne "camion charge" si le tonnage renseigne l'exige et qu'elle est
     * disponible, moyenne generale sinon. Le tonnage restant facultatif a la
     * saisie d'une mission (cargoWeightKg peut etre null), ce repli est
     * systematique -- jamais de blocage faute de tonnage.
     */
    private BigDecimal resolveConsumption(Vehicle vehicle, BigDecimal cargoWeightKg) {
        BigDecimal capacityKg = vehicle.capacityKg();
        if (cargoWeightKg != null && vehicle.getAvgFuelConsumptionLoaded() != null
                && capacityKg != null && capacityKg.signum() > 0) {
            int thresholdPercent = settingService.getInt("fuel.loaded_threshold_percent", 50);
            BigDecimal thresholdKg = capacityKg.multiply(BigDecimal.valueOf(thresholdPercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (cargoWeightKg.compareTo(thresholdKg) >= 0) {
                return vehicle.getAvgFuelConsumptionLoaded();
            }
        }
        return vehicle.getAvgFuelConsumption();
    }

    /** Cherche le corridor dans les deux sens : la distance routiere est la meme aller-retour. */
    private Route findReferenceRoute(Long originCityId, Long destinationCityId) {
        if (originCityId == null || destinationCityId == null) {
            return null;
        }
        return routeService.findByCities(originCityId, destinationCityId)
                .or(() -> routeService.findByCities(destinationCityId, originCityId))
                .orElse(null);
    }

    private record GeoPointPair(double originLat, double originLng, double destLat, double destLng, String source) {
    }

    private record GeoPoint(double lat, double lng, String source) {
    }

    /** Precision croissante — determine quel cote (depart/arrivee) degrade la fiabilite globale de l'estimation. */
    private static final List<String> COORDINATE_PRECISION = List.of("CITY_COORDINATES", "SITE_COORDINATES", "QUARTIER_COORDINATES");

    /**
     * Resout un point (depart ou arrivee, meme logique des deux cotes) en preferant,
     * dans l'ordre, le quartier/marche (le plus specifique — pertinent quand aucun
     * site de l'entreprise n'existe dans cette ville, ex. un marche a Yaounde), puis
     * le site (agence/depot), puis a defaut le simple centre-ville.
     */
    private GeoPoint resolvePoint(Long cityId, Long agencyId, Long quartierId) {
        if (quartierId != null) {
            Quartier quartier = quartierRepository.findById(quartierId).orElse(null);
            if (quartier != null) {
                ensureCoordinates(quartier);
                if (quartier.getLatitude() != null) {
                    return new GeoPoint(quartier.getLatitude().doubleValue(), quartier.getLongitude().doubleValue(),
                            "QUARTIER_COORDINATES");
                }
            }
        }

        Agency agency = agencyId == null ? null : agencyRepository.findById(agencyId).orElse(null);
        if (agency != null) {
            ensureCoordinates(agency);
            if (agency.getLatitude() != null) {
                return new GeoPoint(agency.getLatitude().doubleValue(), agency.getLongitude().doubleValue(),
                        "SITE_COORDINATES");
            }
        }

        City city = cityId == null ? null : cityRepository.findById(cityId).orElse(null);
        if (city != null) {
            ensureCoordinates(city);
            if (city.getLatitude() != null) {
                return new GeoPoint(city.getLatitude().doubleValue(), city.getLongitude().doubleValue(),
                        "CITY_COORDINATES");
            }
        }

        return null;
    }

    private GeoPointPair resolveCoordinates(Long originCityId, Long destinationCityId,
                                            Long originAgencyId, Long destinationAgencyId,
                                            Long originQuartierId, Long destinationQuartierId) {
        GeoPoint origin = resolvePoint(originCityId, originAgencyId, originQuartierId);
        GeoPoint destination = resolvePoint(destinationCityId, destinationAgencyId, destinationQuartierId);
        if (origin == null || destination == null) {
            return null;
        }

        String source = COORDINATE_PRECISION.indexOf(origin.source()) <= COORDINATE_PRECISION.indexOf(destination.source())
                ? origin.source() : destination.source();

        return new GeoPointPair(origin.lat(), origin.lng(), destination.lat(), destination.lng(), source);
    }

    /**
     * Geocode et persiste les coordonnees d'un site si elles manquent
     * encore, pour eviter de refaire l'appel externe a chaque
     * estimation suivante impliquant ce site.
     */
    private void ensureCoordinates(Agency agency) {
        if (agency.getLatitude() != null) {
            return;
        }

        // Le quartier rattache est deja localise (referentiel partage avec les
        // points de livraison) : le reutiliser directement est plus fiable qu'un
        // nouveau geocodage par texte, qui peut retomber sur le centre-ville faute
        // de correspondance exacte pour l'adresse ou le nom du site (deja observe :
        // plusieurs sites distincts geocodes au meme point generique "Douala").
        if (agency.getQuartier() != null && agency.getQuartier().getLatitude() != null) {
            agency.setLatitude(agency.getQuartier().getLatitude());
            agency.setLongitude(agency.getQuartier().getLongitude());
            agencyRepository.save(agency);
            log.info("Site {} localise via son quartier {} ({}, {})",
                    agency.getName(), agency.getQuartier().getName(),
                    agency.getLatitude(), agency.getLongitude());
            return;
        }

        // A defaut de quartier localise, on retente un geocodage par texte —
        // adresse + quartier si les deux sont renseignes, sinon le meilleur repli disponible.
        String place;
        if (agency.getAddress() != null && !agency.getAddress().isBlank()) {
            place = agency.getQuartier() != null
                    ? "%s, %s".formatted(agency.getAddress(), agency.getQuartier().getName())
                    : agency.getAddress();
        } else if (agency.getQuartier() != null) {
            place = agency.getQuartier().getName();
        } else {
            place = agency.getName();
        }

        String query = "%s, %s, Cameroun".formatted(place, agency.getCity().getName());

        geocodingClient.geocode(query).ifPresent(point -> {
            agency.setLatitude(point.latitude());
            agency.setLongitude(point.longitude());
            agencyRepository.save(agency);
            log.info("Site {} geolocalise automatiquement ({}, {})",
                    agency.getName(), point.latitude(), point.longitude());
        });
    }

    /** Meme principe que {@link #ensureCoordinates(Agency)}, pour une ville. */
    private void ensureCoordinates(City city) {
        if (city.getLatitude() != null) {
            return;
        }
        geocodingClient.geocode("%s, Cameroun".formatted(city.getName())).ifPresent(point -> {
            city.setLatitude(point.latitude());
            city.setLongitude(point.longitude());
            cityRepository.save(city);
            log.info("Ville {} geolocalisee automatiquement ({}, {})",
                    city.getName(), point.latitude(), point.longitude());
        });
    }

    /** Meme principe que {@link #ensureCoordinates(Agency)}, pour un quartier. */
    private void ensureCoordinates(Quartier quartier) {
        if (quartier.getLatitude() != null) {
            return;
        }
        geocodingClient.geocode("%s, %s, Cameroun".formatted(quartier.getName(), quartier.getCity().getName()))
                .ifPresent(point -> {
                    quartier.setLatitude(point.latitude());
                    quartier.setLongitude(point.longitude());
                    quartierRepository.save(quartier);
                    log.info("Quartier {} geolocalise automatiquement ({}, {})",
                            quartier.getName(), point.latitude(), point.longitude());
                });
    }

    // ------------------------------------------------------------------
    // Transitions
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public MissionDetailResponse start(Long id) {
        Mission mission = find(id);
        assertTransition(mission, MissionStatus.EN_COURS);

        vehicleService.assertAssignable(mission.getVehicle().getId());

        mission.start();
        // Le passage en EN_MISSION est automatique, jamais saisi (RG-4.3)
        mission.getVehicle().setStatus(VehicleStatus.EN_MISSION);

        log.info("Mission {} demarree par {}", mission.getMissionNumber(), SecurityUtils.currentUserEmail());
        return get(id);
    }

    /**
     * Cloture. Le chiffre d'affaires n'est plus une condition de cloture :
     * le suivi porte sur les charges par camion, pas sur la facturation.
     * Un montant facultatif reste accepte pour qui veut continuer a le
     * renseigner, sans jamais bloquer la cloture s'il est absent.
     */
    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public MissionDetailResponse complete(Long id, MissionCompleteRequest request) {
        Mission mission = find(id);
        assertTransition(mission, MissionStatus.TERMINEE);

        BigDecimal revenueAmount = request.revenueAmount() == null ? BigDecimal.ZERO : request.revenueAmount();

        // Un ecart avec la proposition tarifaire doit etre justifie — seulement si un
        // montant non nul a effectivement ete saisi, pas pour une cloture sans montant.
        BigDecimal proposed = mission.getRevenueAmount();
        if (proposed != null && proposed.signum() > 0
                && revenueAmount.signum() > 0
                && proposed.compareTo(revenueAmount) != 0
                && (request.revenueNote() == null || request.revenueNote().isBlank())) {
            throw new BusinessException("RG-5.5",
                    "Le montant differe de la proposition tarifaire (%s) : une justification est requise"
                            .formatted(proposed),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        BigDecimal previous = mission.getRevenueAmount();
        mission.setRevenueAmount(revenueAmount);
        mission.setRevenueNote(request.revenueNote());

        if (request.distanceKm() != null) {
            mission.setDistanceKm(request.distanceKm());
        }
        if (request.tollCost() != null) {
            mission.setTollCost(request.tollCost());
        }
        if (request.otherCost() != null) {
            mission.setOtherCost(request.otherCost());
        }

        applyDriverCost(mission);
        mission.complete();
        mission.getVehicle().setStatus(VehicleStatus.DISPONIBLE);

        // Compteurs du chauffeur, utilises par la notation
        Driver driver = mission.getDriver();
        driver.setTotalMissions(driver.getTotalMissions() + 1);
        if (mission.getDistanceKm() != null) {
            driver.setTotalKilometers(driver.getTotalKilometers().add(mission.getDistanceKm()));
        }

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.AMOUNT_MODIFIED,
                ENTITY, id, null,
                "\"%s\"".formatted(previous),
                "\"%s\"".formatted(revenueAmount));

        log.info("Mission {} cloturee, CA {} FCFA", mission.getMissionNumber(), revenueAmount);
        return get(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_CANCEL')")
    public MissionDetailResponse cancel(Long id, MissionCancelRequest request) {
        Mission mission = find(id);
        assertTransition(mission, MissionStatus.ANNULEE);

        mission.cancel(request.reason(), request.comment());
        if (mission.getVehicle().getStatus() == VehicleStatus.EN_MISSION) {
            mission.getVehicle().setStatus(VehicleStatus.DISPONIBLE);
        }

        log.info("Mission {} annulee — motif : {}", mission.getMissionNumber(), request.reason());
        return get(id);
    }

    /**
     * Avancement calcule depuis la position GPS (RG-5.4).
     * Appele par le module telematique au sprint 5, jamais saisi.
     */
    @Transactional
    public void updateProgress(Long missionId, BigDecimal traveledKm) {
        Mission mission = find(missionId);
        if (mission.getStatus() != MissionStatus.EN_COURS
                || mission.getDistanceKm() == null || mission.getDistanceKm().signum() == 0) {
            return;
        }
        BigDecimal progress = traveledKm.multiply(BigDecimal.valueOf(100))
                .divide(mission.getDistanceKm(), 2, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100));
        mission.setProgress(progress);
    }

    /** Missions terminees sans CA depuis plus de N heures (RG-5.9). */
    @Transactional(readOnly = true)
    public List<Mission> findMissingRevenue() {
        int hours = settingService.getInt("mission.revenue_reminder_hours", 48);
        return repository.findCompletedWithoutRevenue(Instant.now().minus(Duration.ofHours(hours)));
    }

    // ------------------------------------------------------------------
    // Regles internes
    // ------------------------------------------------------------------

    private void assertTransition(Mission mission, MissionStatus target) {
        if (!mission.getStatus().canTransitionTo(target)) {
            throw new BusinessException("RG-5.3",
                    "Transition impossible : %s vers %s".formatted(mission.getStatus(), target),
                    HttpStatus.CONFLICT);
        }
    }

    /**
     * Chevauchement de periode pour le camion ou le chauffeur (RG-5.3).
     * En l'absence d'arrivee prevue, on retient la seule date de depart.
     * Sans aucune date planifiee (mission lancee au fil de l'eau, sans
     * planification prealable), le chevauchement n'est simplement pas
     * verifiable a la creation.
     */
    private void assertNoOverlap(Long vehicleId, Long driverId,
                                 Instant start, Instant plannedArrival, Long excludeId) {
        if (start == null) {
            return;
        }
        Instant end = plannedArrival == null ? start : plannedArrival;
        long conflicts = repository.countOverlapping(vehicleId, driverId, start, end, excludeId);
        if (conflicts > 0) {
            throw new BusinessException("RG-5.3",
                    "Le camion ou le chauffeur est deja engage sur cette periode",
                    HttpStatus.CONFLICT);
        }
    }

    /** Montant propose par la grille tarifaire (RG-5.5). */
    private void applyProposedRevenue(Mission mission) {
        if (!Boolean.TRUE.equals(mission.getServiceType().getBillable())) {
            return;
        }
        Optional<Tariff> tariff = tariffService.resolve(
                mission.getClient() == null ? null : mission.getClient().getId(),
                mission.getServiceType().getId(),
                mission.getRoute() == null ? null : mission.getRoute().getId(),
                LocalDate.now());

        tariff.ifPresent(found -> {
            mission.setTariff(found);
            mission.setRevenueAmount(found.compute(mission.getDistanceKm(), mission.getCargoWeightKg()));
            mission.setRevenueNote("Propose par la grille tarifaire");
        });
    }

    /**
     * Quote-part chauffeur : salaire journalier multiplie par la duree
     * de la mission. Sans salaire renseigne, le cout reste a zero
     * plutot que d'etre invente.
     */
    private void applyDriverCost(Mission mission) {
        if (!settingService.getBoolean("mission.driver_cost_auto", true)) {
            return;
        }
        BigDecimal salary = mission.getDriver().getMonthlySalary();
        if (salary == null || salary.signum() == 0 || mission.getActualStart() == null) {
            return;
        }

        long minutes = Duration.between(mission.getActualStart(), Instant.now()).toMinutes();
        BigDecimal days = BigDecimal.valueOf(Math.max(1, Math.ceil(minutes / 1440.0)));
        BigDecimal dailyRate = salary.divide(BigDecimal.valueOf(22), 2, RoundingMode.HALF_UP);

        mission.setDriverCost(dailyRate.multiply(days).setScale(2, RoundingMode.HALF_UP));
    }

    /** Numero au format MS-AAAA-NNN, sequence annuelle (RG-5.2). */
    private String nextMissionNumber() {
        String prefix = settingService.getString("mission.number_prefix", "MS");
        int year = Year.now().getValue();
        String pattern = "%s-%d-%%".formatted(prefix, year);
        long count = repository.countByNumberPrefix(pattern);
        return "%s-%d-%03d".formatted(prefix, year, count + 1);
    }

    private boolean canSeeFinancials() {
        return SecurityUtils.hasPermission("FINANCE_READ")
                || SecurityUtils.hasPermission("MISSION_UPDATE");
    }

    private BigDecimal percentage(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private com.sogeco.fleet.modules.city.City resolveCity(Long id) {
        return id == null ? null : cityRepository.findById(id).orElse(null);
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", id));
    }

    private Driver findDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", id));
    }

    private ServiceType findServiceType(Long id) {
        return serviceTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type de prestation", id));
    }

    Mission find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", id));
    }

    // ------------------------------------------------------------------
    // Etapes
    // ------------------------------------------------------------------

    /**
     * Chargement complementaire signale en cours de route : le gestionnaire
     * demande au chauffeur de passer par un autre site avant sa destination.
     * Trace l'etape et corrige la distance prevue de la mission — sans quoi
     * l'avancement (RG-5.4, calcule depuis cette distance) se bloquerait a
     * 100% avant l'arrivee reelle, le detour n'etant jamais reflete.
     */
    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public MissionDetailResponse addDetour(Long missionId, MissionDetourRequest request) {
        Mission mission = find(missionId);

        if (mission.getStatus() != MissionStatus.EN_COURS) {
            throw new BusinessException("RG-5.4",
                    "Un chargement complementaire ne peut etre signale que sur une mission en cours",
                    HttpStatus.CONFLICT);
        }

        Agency site = agencyRepository.findById(request.agencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Site", request.agencyId()));
        ensureCoordinates(site);

        int nextSequence = waypointRepository.findByMissionIdOrderBySequenceNumberAsc(missionId).size() + 1;
        waypointRepository.save(MissionWaypoint.builder()
                .mission(mission)
                .sequenceNumber(nextSequence)
                .label("Chargement complementaire — " + site.getName())
                .address(site.getAddress())
                .latitude(site.getLatitude())
                .longitude(site.getLongitude())
                .notes(request.notes())
                .build());

        correctDistanceForDetour(mission, site);

        log.info("Detour de chargement ajoute a la mission {} : site {}", mission.getMissionNumber(), site.getName());
        return get(missionId);
    }

    /**
     * Origine -> site de chargement -> destination, a la place du trajet
     * direct initialement estime. N'ecrase jamais a la baisse une distance
     * deja plus grande (mesure GPS en cours, ou detour precedent) : un
     * detour supplementaire rallonge toujours le trajet, il ne le
     * raccourcit jamais. Sans coordonnees exploitables d'un cote ou de
     * l'autre, la distance reste inchangee plutot que d'etre corrigee au hasard.
     */
    private void correctDistanceForDetour(Mission mission, Agency detourSite) {
        if (detourSite.getLatitude() == null || detourSite.getLongitude() == null) {
            return;
        }
        GeoPoint origin = resolvePoint(
                mission.getOriginCity() == null ? null : mission.getOriginCity().getId(),
                mission.getAgency() == null ? null : mission.getAgency().getId(),
                null);
        GeoPoint destination = resolvePoint(
                mission.getDestinationCity() == null ? null : mission.getDestinationCity().getId(),
                mission.getDestinationAgency() == null ? null : mission.getDestinationAgency().getId(),
                null);
        if (origin == null || destination == null) {
            return;
        }

        GeoPoint detour = new GeoPoint(
                detourSite.getLatitude().doubleValue(), detourSite.getLongitude().doubleValue(), "SITE_COORDINATES");

        BigDecimal viaDetour = roadDistanceKm(origin, detour).add(roadDistanceKm(detour, destination));

        if (mission.getDistanceKm() == null || viaDetour.compareTo(mission.getDistanceKm()) > 0) {
            mission.setDistanceKm(viaDetour);
        }
    }

    /** Meme logique que computeEstimate : routage reel si disponible, sinon vol d'oiseau majore de 20%. */
    private BigDecimal roadDistanceKm(GeoPoint origin, GeoPoint destination) {
        return routingClient.route(
                        BigDecimal.valueOf(origin.lat()), BigDecimal.valueOf(origin.lng()),
                        BigDecimal.valueOf(destination.lat()), BigDecimal.valueOf(destination.lng()))
                .map(OpenRouteServiceClient.RoadRoute::distanceKm)
                .orElseGet(() -> BigDecimal.valueOf(
                                GeoUtils.distanceKm(origin.lat(), origin.lng(), destination.lat(), destination.lng()))
                        .multiply(ROAD_DISTANCE_FACTOR).setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public WaypointResponse addWaypoint(Long missionId, WaypointRequest request) {
        Mission mission = find(missionId);

        MissionWaypoint waypoint = MissionWaypoint.builder()
                .mission(mission)
                .sequenceNumber(request.sequenceNumber())
                .label(request.label())
                .address(request.address())
                .plannedArrival(request.plannedArrival())
                .notes(request.notes())
                .build();

        if (request.coordinates() != null && !request.coordinates().isBlank()) {
            CoordinateParser.GeoPoint point = CoordinateParser.parse(request.coordinates());
            waypoint.setLatitude(point.latitude());
            waypoint.setLongitude(point.longitude());
        }

        return WaypointResponse.from(waypointRepository.save(waypoint));
    }

    @Transactional
    @PreAuthorize("hasAuthority('MISSION_UPDATE')")
    public void deleteWaypoint(Long waypointId) {
        waypointRepository.deleteById(waypointId);
    }
}
