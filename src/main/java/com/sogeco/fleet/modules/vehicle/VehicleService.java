package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.document.DocumentService;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.InsurancePolicy;
import com.sogeco.fleet.modules.insurance.InsurancePolicyRepository;
import com.sogeco.fleet.modules.insurance.TechnicalInspectionRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.dto.*;
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
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleService {

    private static final String ENTITY = "Vehicle";

    private final VehicleRepository repository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final CityRepository cityRepository;
    private final DocumentService documentService;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final TechnicalInspectionRepository technicalInspectionRepository;
    private final AuditService auditService;
    private final SettingService settingService;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    public PageResponse<VehicleResponse> list(Pageable pageable) {
        // Une seule requete pour toutes les affectations actives, plutot
        // qu'une par camion : c'est la difference entre 1 et N requetes.
        Map<Long, VehicleAssignment> byVehicle = activeAssignmentsByVehicle();

        // Un gestionnaire non-administrateur ne voit que les camions de sa ville.
        Page<Vehicle> page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByCity_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));

        return PageResponse.from(page, vehicle -> {
            VehicleAssignment assignment = byVehicle.get(vehicle.getId());
            return VehicleResponse.from(vehicle,
                    assignment == null ? null : assignment.getDriver().getId(),
                    assignment == null ? null : assignment.getDriver().getFullName());
        });
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    public VehicleDetailResponse get(Long id) {
        Vehicle vehicle = find(id);
        VehicleAssignment assignment = assignmentRepository.findByVehicleIdAndEndDateIsNull(id).orElse(null);
        List<String> blocking = blockingReasons(vehicle);

        return VehicleDetailResponse.of(
                vehicle,
                assignment == null ? null : assignment.getDriver().getId(),
                assignment == null ? null : assignment.getDriver().getFullName(),
                assignment == null ? null : assignment.getDriver().getPhone(),
                blocking.isEmpty() && vehicle.isAssignable(),
                blocking,
                documentService.listFor(EntityType.VEHICLE, id));
    }

    /** Barre de recherche du tableau de bord — immatriculation, marque ou modele. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    public List<VehicleResponse> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        Map<Long, VehicleAssignment> byVehicle = activeAssignmentsByVehicle();

        return repository.search(q.trim(), org.springframework.data.domain.PageRequest.of(0, 10)).stream()
                .map(vehicle -> {
                    VehicleAssignment assignment = byVehicle.get(vehicle.getId());
                    return VehicleResponse.from(vehicle,
                            assignment == null ? null : assignment.getDriver().getId(),
                            assignment == null ? null : assignment.getDriver().getFullName());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    public VehicleStatsResponse stats() {
        Map<VehicleStatus, Long> counts = new EnumMap<>(VehicleStatus.class);
        for (Object[] row : repository.countGroupedByStatus()) {
            counts.put((VehicleStatus) row[0], (Long) row[1]);
        }

        long total = repository.countByActiveTrue();
        long immobilized = counts.getOrDefault(VehicleStatus.EN_MAINTENANCE, 0L)
                + counts.getOrDefault(VehicleStatus.EN_PANNE, 0L)
                + counts.getOrDefault(VehicleStatus.HORS_SERVICE, 0L);

        BigDecimal availability = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(total - immobilized)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);

        return new VehicleStatsResponse(
                total,
                counts.getOrDefault(VehicleStatus.EN_MISSION, 0L),
                counts.getOrDefault(VehicleStatus.DISPONIBLE, 0L),
                counts.getOrDefault(VehicleStatus.EN_MAINTENANCE, 0L),
                counts.getOrDefault(VehicleStatus.EN_PANNE, 0L),
                counts.getOrDefault(VehicleStatus.HORS_SERVICE, 0L),
                availability);
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_CREATE')")
    public VehicleDetailResponse create(VehicleRequest request) {
        if (repository.existsByRegistrationNumberIgnoreCase(request.registrationNumber())) {
            throw new DuplicateResourceException("Camion", "immatriculation", request.registrationNumber());
        }
        if (request.vinNumber() != null && repository.existsByVinNumberIgnoreCase(request.vinNumber())) {
            throw new DuplicateResourceException("Camion", "chassis", request.vinNumber());
        }

        Vehicle vehicle = Vehicle.builder()
                .registrationNumber(request.registrationNumber().toUpperCase())
                .vinNumber(request.vinNumber())
                .brand(request.brand())
                .model(request.model())
                .bodyType(request.bodyType())
                .capacityTons(request.capacityTons())
                .tankCapacityLiters(request.tankCapacityLiters())
                .grossWeightKg(request.grossWeightKg())
                .firstRegistrationDate(request.firstRegistrationDate())
                .ownerName(request.ownerName())
                .purchaseDate(request.purchaseDate())
                .purchasePrice(request.purchasePrice())
                .city(resolveCity(request.cityId()))
                .deviceId(emptyToNull(request.deviceId()))
                .currentKilometers(request.currentKilometers() == null
                        ? BigDecimal.ZERO : request.currentKilometers())
                .status(VehicleStatus.DISPONIBLE)
                .usageType(request.usageType())
                .weeklyWashCost(resolveWeeklyWashCost(request.usageType(), request.weeklyWashCost()))
                .build();

        Vehicle saved = repository.save(vehicle);
        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.VEHICLE_CREATED, ENTITY, saved.getId(), null);
        log.info("Camion {} cree par {}", saved.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return get(saved.getId());
    }

    /**
     * L'immatriculation et le chassis ne sont pas modifiables apres
     * creation (RG-4.1) : ils identifient administrativement le vehicule.
     */
    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    public VehicleDetailResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = find(id);

        EditWindowGuard.assertEditable(vehicle.getCreatedAt(),
                settingService.getInt("vehicle.edit_window_hours", 1), "RG-4-EDIT", "Ce camion");

        if (!vehicle.getRegistrationNumber().equalsIgnoreCase(request.registrationNumber())) {
            throw new BusinessException("RG-4.1",
                    "L'immatriculation n'est pas modifiable apres creation", HttpStatus.CONFLICT);
        }

        vehicle.setBrand(request.brand());
        vehicle.setModel(request.model());
        vehicle.setBodyType(request.bodyType());
        vehicle.setCapacityTons(request.capacityTons());
        vehicle.setTankCapacityLiters(request.tankCapacityLiters());
        vehicle.setGrossWeightKg(request.grossWeightKg());
        vehicle.setFirstRegistrationDate(request.firstRegistrationDate());
        vehicle.setOwnerName(request.ownerName());
        vehicle.setPurchaseDate(request.purchaseDate());
        vehicle.setPurchasePrice(request.purchasePrice());
        vehicle.setCity(resolveCity(request.cityId()));
        vehicle.setDeviceId(emptyToNull(request.deviceId()));
        vehicle.setUsageType(request.usageType());
        vehicle.setWeeklyWashCost(resolveWeeklyWashCost(request.usageType(), request.weeklyWashCost()));

        if (request.currentKilometers() != null) {
            try {
                vehicle.updateKilometers(request.currentKilometers());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("RG-4.6", e.getMessage(), HttpStatus.UNPROCESSABLE_CONTENT);
            }
        }

        return get(id);
    }

    /**
     * Changement de statut manuel. Le passage en EN_MISSION est exclu :
     * il releve du cycle de vie des missions (RG-4.3).
     */
    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    public VehicleDetailResponse changeStatus(Long id, VehicleStatus status) {
        Vehicle vehicle = find(id);

        if (status == VehicleStatus.EN_MISSION) {
            throw new BusinessException("RG-4.3",
                    "Le statut En mission est pilote par les missions, il ne se saisit pas",
                    HttpStatus.CONFLICT);
        }
        if (vehicle.getStatus() == VehicleStatus.EN_MISSION) {
            throw new BusinessException("RG-4.3",
                    "Ce camion est en mission, terminez ou annulez la mission d'abord",
                    HttpStatus.CONFLICT);
        }

        vehicle.setStatus(status);
        return get(id);
    }

    /** Correction a la baisse, reservee aux administrateurs et tracee. */
    @Transactional
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public VehicleDetailResponse correctOdometer(Long id, OdometerCorrectionRequest request) {
        Vehicle vehicle = find(id);
        BigDecimal previous = vehicle.getCurrentKilometers();

        vehicle.forceKilometers(request.kilometers());

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.ODOMETER_CORRECTED,
                ENTITY, id, null,
                "\"%s\"".formatted(previous),
                "\"%s | %s\"".formatted(request.kilometers(), request.motif()));

        log.warn("Kilometrage du camion {} corrige de {} a {} par {} — motif : {}",
                vehicle.getRegistrationNumber(), previous, request.kilometers(),
                SecurityUtils.currentUserEmail(), request.motif());

        return get(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_DELETE')")
    public void deactivate(Long id) {
        Vehicle vehicle = find(id);

        if (vehicle.getStatus() == VehicleStatus.EN_MISSION) {
            throw new BusinessException("RG-4.7",
                    "Impossible de desactiver un camion en mission", HttpStatus.CONFLICT);
        }

        assignmentRepository.findByVehicleIdAndEndDateIsNull(id)
                .ifPresent(assignment -> assignment.close(LocalDate.now()));

        vehicle.deactivate();
        vehicle.setStatus(VehicleStatus.HORS_SERVICE);
    }

    // ------------------------------------------------------------------
    // Conformite
    // ------------------------------------------------------------------

    /**
     * Motifs empechant l'affectation d'une mission.
     *
     * C'est le controle le plus rentable de l'application : il evite
     * qu'un camion parte avec une assurance expiree (RG-4.5).
     *
     * Deux sources independantes, toutes deux verifiees ici plutot
     * que l'une via l'autre : les documents generiques (carte grise,
     * chronotachygraphe, et assurance/visite avant que le sprint 6
     * n'existe), et depuis le sprint 6 les polices d'assurance et
     * visites techniques elles-memes, qui portent une echeance propre
     * independante de tout document televerse. Comme pour les
     * documents, seule une police ou une visite PRESENTE ET EXPIREE
     * bloque — une absence n'a jamais bloque, decision deja actee.
     */
    @Transactional(readOnly = true)
    public List<String> blockingReasons(Vehicle vehicle) {
        List<String> reasons = new ArrayList<>();

        if (!Boolean.TRUE.equals(vehicle.getActive())) {
            reasons.add("Camion desactive");
        }
        if (!vehicle.getStatus().isAssignable()) {
            reasons.add("Statut : " + vehicle.getStatus());
        }

        for (Document document : documentService.findBlockingExpired(EntityType.VEHICLE, vehicle.getId())) {
            reasons.add("%s expire depuis %d jours".formatted(
                    label(document.getDocumentType()), Math.abs(document.daysRemaining())));
        }

        insurancePolicyRepository.findActiveForVehicle(vehicle.getId())
                .filter(InsurancePolicy::isExpired)
                .ifPresent(policy -> reasons.add("Assurance expiree depuis %d jours"
                        .formatted(Math.abs(policy.daysRemaining()))));

        technicalInspectionRepository.findLatestForVehicle(vehicle.getId())
                .ifPresent(inspection -> {
                    if (inspection.getResult().blocksVehicle()) {
                        reasons.add("Derniere visite technique non conforme");
                    } else if (inspection.getNextInspectionDate().isBefore(LocalDate.now())) {
                        reasons.add("Visite technique expiree depuis %d jours"
                                .formatted(Math.abs(inspection.daysUntilNext())));
                    }
                });

        return reasons;
    }

    /** Appele par le module missions au sprint 3. */
    @Transactional(readOnly = true)
    public void assertAssignable(Long vehicleId) {
        List<String> reasons = blockingReasons(find(vehicleId));
        if (!reasons.isEmpty()) {
            throw new BusinessException("RG-4.5",
                    "Ce camion ne peut pas etre affecte : " + String.join(" ; ", reasons),
                    HttpStatus.CONFLICT);
        }
    }

    // ------------------------------------------------------------------

    private Map<Long, VehicleAssignment> activeAssignmentsByVehicle() {
        Map<Long, VehicleAssignment> map = new HashMap<>();
        assignmentRepository.findByEndDateIsNull()
                .forEach(assignment -> map.put(assignment.getVehicle().getId(), assignment));
        return map;
    }

    private String label(DocumentType type) {
        return switch (type) {
            case ASSURANCE -> "Assurance";
            case VISITE_TECHNIQUE -> "Visite technique";
            case CARTE_GRISE -> "Carte grise";
            case CHRONOTACHYGRAPHE -> "Chronotachygraphe";
            default -> type.name();
        };
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", cityId));
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Un frais de lavage n'a de sens que pour un camion en tour de ville. */
    private BigDecimal resolveWeeklyWashCost(UsageType usageType, BigDecimal weeklyWashCost) {
        return usageType == UsageType.TOUR_VILLE ? weeklyWashCost : null;
    }

    Vehicle find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", id));
    }

    Driver findDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", id));
    }
}
