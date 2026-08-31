package com.sogeco.fleet.modules.fuel;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.FuelLogStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.document.DocumentRepository;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.fuel.dto.*;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.partner.PartnerRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.GpsDailyStatRepository;
import com.sogeco.fleet.modules.tracking.GpsPositionRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Saisie et controle des ravitaillements.
 *
 * Regles couvertes : RG-6.1 a RG-6.11.
 *
 * La fiabilite de tout le module repose sur la rigueur du releve
 * kilometrique : un kilometrage approximatif rend les consommations
 * inexploitables et fait apparaitre des anomalies qui n'en sont pas.
 * D'ou les controles stricts a la saisie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelService {

    private final FuelLogRepository repository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final MissionRepository missionRepository;
    private final PartnerRepository partnerRepository;
    private final DocumentRepository documentRepository;
    private final SettingService settingService;
    private final GpsPositionRepository gpsPositionRepository;
    private final GpsDailyStatRepository gpsDailyStatRepository;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public PageResponse<FuelLogResponse> list(Pageable pageable) {
        Page<FuelLog> page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));
        return PageResponse.from(page, FuelLogResponse::from);
    }

    /**
     * Le camion lui-meme n'est pas verifie ici : un gestionnaire qui
     * consulte un camion d'une autre ville par son id obtient une liste
     * vide (aucun plein ne remonte), jamais une erreur revelant que ce
     * camion existe ailleurs.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public List<FuelLogResponse> listForVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByFuelDatetimeDesc(vehicleId).stream()
                .filter(this::inCurrentScope)
                .map(FuelLogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public List<FuelLogResponse> anomalies() {
        List<FuelLog> logs = SecurityUtils.currentCityId()
                .map(cityId -> repository.findByStatusAndVehicle_City_IdOrderByFuelDatetimeDesc(
                        FuelLogStatus.ANOMALIE, cityId))
                .orElseGet(() -> repository.findByStatusOrderByFuelDatetimeDesc(FuelLogStatus.ANOMALIE));
        return logs.stream().map(FuelLogResponse::from).toList();
    }

    /** Vrai si le camion du plein est dans la ville geree, ou si l'appelant voit tout (admin). */
    private boolean inCurrentScope(FuelLog log) {
        return SecurityUtils.currentCityId()
                .map(cityId -> log.getVehicle().getCity() != null && cityId.equals(log.getVehicle().getCity().getId()))
                .orElse(true);
    }

    // ------------------------------------------------------------------
    // Saisie
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('FUEL_CREATE')")
    public FuelLogResponse create(FuelLogRequest request) {
        Vehicle vehicle = findVehicle(request.vehicleId());

        BigDecimal before = request.odometerBefore() == null
                ? vehicle.getCurrentKilometers()
                : request.odometerBefore();

        validate(vehicle, before, request);

        // Le "Km avant" saisi peut differer du kilometrage reel du camion
        // (champ optionnel, parfois absent ou approximatif) : verifie ici
        // contre la valeur qui fait autorite, avant que updateKilometers()
        // ne rejette la baisse plus loin avec une exception non geree.
        if (vehicle.getCurrentKilometers() != null
                && request.odometerAfter().compareTo(vehicle.getCurrentKilometers()) < 0) {
            throw new BusinessException("RG-6.4",
                    "Le kilometrage apres le plein (%s) est inferieur au kilometrage actuel du camion (%s)"
                            .formatted(request.odometerAfter(), vehicle.getCurrentKilometers()),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        Optional<FuelLog> previous = repository.findPreviousLog(vehicle.getId(), request.fuelDatetime(), null);
        BigDecimal distance = resolveDistanceKm(vehicle,
                previous.map(FuelLog::getFuelDatetime).orElse(null), request.fuelDatetime(),
                before, request.odometerAfter());

        FuelLog log = FuelLog.builder()
                .vehicle(vehicle)
                .driver(resolveDriver(request.driverId()))
                .mission(resolveMission(request.missionId(), vehicle, request.fuelDatetime()))
                .station(request.partnerId() == null ? null : partnerRepository.findById(request.partnerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Station", request.partnerId())))
                .fuelDatetime(request.fuelDatetime())
                .quantityLiters(request.quantityLiters())
                .unitPrice(request.unitPrice())
                .totalCost(request.quantityLiters().multiply(request.unitPrice())
                        .setScale(2, RoundingMode.HALF_UP))
                .odometerBefore(before)
                .odometerAfter(request.odometerAfter())
                .fullTank(request.fullTank() == null || request.fullTank())
                .receiptNumber(request.receiptNumber())
                .receipt(request.receiptDocumentId() == null ? null
                        : documentRepository.findById(request.receiptDocumentId()).orElse(null))
                .status(FuelLogStatus.VALIDE)
                .build();

        log.setComputedConsumption(log.computeConsumption(distance));

        detectAnomalies(log, vehicle);

        FuelLog saved = repository.save(log);

        // Le kilometrage du camion suit le releve du plein.
        vehicle.updateKilometers(request.odometerAfter());
        refreshVehicleAverage(vehicle);

        // Imputation du cout a la mission rattachee.
        if (saved.getMission() != null) {
            Mission mission = saved.getMission();
            mission.setFuelCost(mission.getFuelCost().add(saved.getTotalCost()));
        }

        return FuelLogResponse.from(saved);
    }

    /**
     * Modification. Ouverte au gestionnaire pendant un delai parametre,
     * puis reservee aux administrateurs (RG-6.10).
     */
    @Transactional
    @PreAuthorize("hasAuthority('FUEL_UPDATE')")
    public FuelLogResponse update(Long id, FuelLogRequest request) {
        FuelLog existing = find(id);

        EditWindowGuard.assertEditable(existing.getCreatedAt(),
                settingService.getInt("fuel.edit_window_hours", 1), "RG-6.10", "Ce plein");

        Vehicle vehicle = existing.getVehicle();
        BigDecimal previousCost = existing.getTotalCost();

        validate(vehicle, request.odometerBefore(), request);

        Optional<FuelLog> previous = repository.findPreviousLog(
                vehicle.getId(), request.fuelDatetime(), existing.getId());
        BigDecimal distance = resolveDistanceKm(vehicle,
                previous.map(FuelLog::getFuelDatetime).orElse(null), request.fuelDatetime(),
                request.odometerBefore(), request.odometerAfter());

        existing.setFuelDatetime(request.fuelDatetime());
        existing.setQuantityLiters(request.quantityLiters());
        existing.setUnitPrice(request.unitPrice());
        existing.setTotalCost(request.quantityLiters().multiply(request.unitPrice())
                .setScale(2, RoundingMode.HALF_UP));
        existing.setOdometerBefore(request.odometerBefore());
        existing.setOdometerAfter(request.odometerAfter());
        existing.setFullTank(request.fullTank() == null || request.fullTank());
        existing.setReceiptNumber(request.receiptNumber());
        existing.setComputedConsumption(existing.computeConsumption(distance));
        existing.setStatus(FuelLogStatus.VALIDE);
        existing.setAnomalyReason(null);

        detectAnomalies(existing, vehicle);
        refreshVehicleAverage(vehicle);

        if (existing.getMission() != null) {
            Mission mission = existing.getMission();
            mission.setFuelCost(mission.getFuelCost()
                    .subtract(previousCost).add(existing.getTotalCost()));
        }

        return FuelLogResponse.from(existing);
    }

    /** L'annulation exige un motif (RG-6.6) et retire le cout de la mission. */
    @Transactional
    @PreAuthorize("hasAuthority('FUEL_UPDATE')")
    public FuelLogResponse cancel(Long id, FuelCancelRequest request) {
        FuelLog fuelLog = find(id);

        if (fuelLog.getStatus() == FuelLogStatus.ANNULE) {
            throw new BusinessException("RG-6.6", "Ce plein est deja annule", HttpStatus.CONFLICT);
        }

        if (fuelLog.getMission() != null) {
            Mission mission = fuelLog.getMission();
            mission.setFuelCost(mission.getFuelCost().subtract(fuelLog.getTotalCost()).max(BigDecimal.ZERO));
        }

        fuelLog.setStatus(FuelLogStatus.ANNULE);
        fuelLog.setCancellationReason(request.reason());
        refreshVehicleAverage(fuelLog.getVehicle());

        log.info("Plein {} annule par {} — motif : {}", id, SecurityUtils.currentUserEmail(), request.reason());
        return FuelLogResponse.from(fuelLog);
    }

    // ------------------------------------------------------------------
    // Distance (RG-6.2)
    // ------------------------------------------------------------------

    /**
     * Distance retenue pour le calcul de consommation. Le GPS fait
     * autorite ; l'ecart d'odometre ne sert que de repli quand aucune
     * donnee GPS exploitable n'existe sur la periode (camion sans
     * boitier, coupure de signal, ou absence de plein precedent
     * servant de reference temporelle).
     */
    private BigDecimal resolveDistanceKm(Vehicle vehicle, Instant from, Instant to,
                                         BigDecimal odometerBefore, BigDecimal odometerAfter) {
        BigDecimal gps = from == null ? null : gpsDistance(vehicle.getId(), from, to);
        if (gps != null && gps.signum() > 0) {
            return gps;
        }
        if (odometerBefore == null || odometerAfter == null) {
            return null;
        }
        return odometerAfter.subtract(odometerBefore);
    }

    private BigDecimal gpsDistance(Long vehicleId, Instant from, Instant to) {
        int retentionDays = settingService.getInt("gps.retention_days", 90);
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        if (from.isBefore(cutoff)) {
            // Positions brutes deja purgees : repli sur l'agregat journalier.
            return gpsDailyStatRepository.totalDistance(vehicleId,
                    LocalDate.ofInstant(from, ZoneOffset.UTC), LocalDate.ofInstant(to, ZoneOffset.UTC));
        }
        return gpsPositionRepository.sumDistance(vehicleId, from, to);
    }

    // ------------------------------------------------------------------
    // Controles
    // ------------------------------------------------------------------

    private void validate(Vehicle vehicle, BigDecimal before, FuelLogRequest request) {

        // Le kilometrage ne peut que croitre (RG-6.4)
        if (before != null && request.odometerAfter().compareTo(before) < 0) {
            throw new BusinessException("RG-6.4",
                    "Le kilometrage apres le plein (%s) est inferieur au precedent (%s)"
                            .formatted(request.odometerAfter(), before),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        // La quantite ne peut exceder la capacite du reservoir (RG-6.5)
        BigDecimal capacity = vehicle.getTankCapacityLiters();
        if (capacity != null && request.quantityLiters().compareTo(capacity) > 0) {
            throw new BusinessException("RG-6.5",
                    "La quantite (%s L) depasse la capacite du reservoir (%s L)"
                            .formatted(request.quantityLiters(), capacity),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        if (request.fuelDatetime().isAfter(Instant.now())) {
            throw new BusinessException("RG-6.1",
                    "La date du plein est dans le futur", HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    /**
     * Detection de surconsommation (RG-6.7).
     *
     * Comparaison a la moyenne mobile des N derniers pleins du MEME
     * camion : comparer deux vehicules de gabarits differents n'aurait
     * aucun sens.
     */
    private void detectAnomalies(FuelLog fuelLog, Vehicle vehicle) {
        BigDecimal consumption = fuelLog.getComputedConsumption();
        if (consumption == null) {
            return;
        }

        int historySize = settingService.getInt("fuel.consumption_history_size", 5);
        int threshold = settingService.getInt("alert.overconsumption_percent", 20);

        List<FuelLog> history = repository.findRecentConsumptions(
                vehicle.getId(), PageRequest.of(0, historySize));

        if (history.size() < 2) {
            // Pas assez d'historique : mieux vaut ne rien signaler que
            // produire une fausse alerte des le deuxieme plein.
            return;
        }

        BigDecimal average = history.stream()
                .map(FuelLog::getComputedConsumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(history.size()), 2, RoundingMode.HALF_UP);

        if (average.signum() == 0) {
            return;
        }

        BigDecimal deviation = consumption.subtract(average)
                .multiply(BigDecimal.valueOf(100))
                .divide(average, 1, RoundingMode.HALF_UP);

        if (deviation.compareTo(BigDecimal.valueOf(threshold)) > 0) {
            fuelLog.markAnomaly("Consommation de %s L/100km, soit +%s%% par rapport a la moyenne de %s"
                    .formatted(consumption, deviation, average));
            log.warn("Surconsommation detectee sur {} : {} L/100km contre {} en moyenne",
                    vehicle.getRegistrationNumber(), consumption, average);
        }
    }

    /** Moyenne du camion, affichee sur la fiche et la liste. */
    private void refreshVehicleAverage(Vehicle vehicle) {
        int historySize = settingService.getInt("fuel.consumption_history_size", 5);
        List<FuelLog> history = repository.findRecentConsumptions(
                vehicle.getId(), PageRequest.of(0, historySize));

        vehicle.setAvgFuelConsumption(average(history));
        vehicle.setAvgFuelConsumptionLoaded(loadedAverage(vehicle, historySize));
    }

    /**
     * Moyenne restreinte aux pleins dont la mission rattachee porte un
     * tonnage significatif -- le tonnage etant facultatif a la saisie
     * d'une mission, cette moyenne reste nulle tant qu'aucun plein charge
     * n'est disponible, sans jamais bloquer quoi que ce soit.
     *
     * Le seuil "charge" est relatif a la capacite du camion (pourcentage
     * parametre), pas une valeur fixe en kg : un tricycle a 200 kg et un
     * semi-remorque a 20 t n'ont pas la meme notion de "charge".
     */
    private BigDecimal loadedAverage(Vehicle vehicle, int historySize) {
        BigDecimal capacityKg = vehicle.capacityKg();
        if (capacityKg == null || capacityKg.signum() <= 0) {
            return null;
        }

        int thresholdPercent = settingService.getInt("fuel.loaded_threshold_percent", 50);
        BigDecimal thresholdKg = capacityKg.multiply(BigDecimal.valueOf(thresholdPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Bassin plus large que la moyenne generale : une partie des pleins
        // avec mission n'atteindra pas le seuil et sera ecartee ci-dessous.
        List<FuelLog> candidates = repository.findRecentWithCargoWeight(
                vehicle.getId(), PageRequest.of(0, historySize * 4));

        List<FuelLog> loaded = candidates.stream()
                .filter(log -> log.getMission().getCargoWeightKg().compareTo(thresholdKg) >= 0)
                .limit(historySize)
                .toList();

        return average(loaded);
    }

    /** Nulle si {@code logs} est vide -- une moyenne sur zero plein n'a pas de sens. */
    private BigDecimal average(List<FuelLog> logs) {
        if (logs.isEmpty()) {
            return null;
        }
        return logs.stream()
                .map(FuelLog::getComputedConsumption)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(logs.size()), 2, RoundingMode.HALF_UP);
    }

    /** Rattachement automatique a la mission en cours (RG-6.9). */
    private Mission resolveMission(Long missionId, Vehicle vehicle, Instant when) {
        if (missionId != null) {
            return missionRepository.findById(missionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mission", missionId));
        }
        List<Mission> active = missionRepository.findActiveByVehicle(vehicle.getId());
        return active.size() == 1 ? active.get(0) : null;
    }

    private Driver resolveDriver(Long driverId) {
        return driverId == null ? null : driverRepository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", driverId));
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", id));
    }

    FuelLog find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plein", id));
    }

    /** Dernier plein complet, expose pour pre-remplir le formulaire. */
    @Transactional(readOnly = true)
    public Optional<FuelLog> previousFullTank(Long vehicleId) {
        return repository.findPreviousFullTank(vehicleId, Instant.now());
    }
}
