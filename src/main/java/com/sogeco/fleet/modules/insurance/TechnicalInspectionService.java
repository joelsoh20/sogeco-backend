package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.dto.TechnicalInspectionRequest;
import com.sogeco.fleet.modules.insurance.dto.TechnicalInspectionResponse;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.partner.PartnerRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleAssignment;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentRepository;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Visites techniques.
 *
 * Regles couvertes : RG-8.4 a RG-8.6. Une non-conformite bloque
 * l'affectation au meme titre qu'une assurance expiree, verifie
 * directement par VehicleService.blockingReasons().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechnicalInspectionService {

    private final TechnicalInspectionRepository repository;
    private final PartnerRepository partnerRepository;
    private final VehicleRepository vehicleRepository;
    private final SettingService settingService;
    private final DriverRepository driverRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<TechnicalInspectionResponse> list(Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));
        return PageResponse.from(page, TechnicalInspectionResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public TechnicalInspectionResponse get(Long id) {
        return TechnicalInspectionResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<TechnicalInspectionResponse> forVehicle(Long vehicleId) {
        if (!inScope(vehicleId)) {
            return List.of();
        }
        return repository.findByVehicleIdOrderByInspectionDateDesc(vehicleId)
                .stream().map(TechnicalInspectionResponse::from).toList();
    }

    /** Ce que le chauffeur connecte a lui-meme saisi — espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public List<TechnicalInspectionResponse> mine() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        return repository.findByCreatedByUserIdOrderByInspectionDateDesc(userId)
                .stream().map(TechnicalInspectionResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE') or hasAuthority('SELF_MANAGE')")
    public TechnicalInspectionResponse create(TechnicalInspectionRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        if (!SecurityUtils.hasPermission("INSURANCE_CREATE")) {
            assertOwnVehicle(vehicle.getId());
        }

        LocalDate next = request.nextInspectionDate() != null
                ? request.nextInspectionDate()
                : request.inspectionDate().plusMonths(
                        settingService.getInt("compliance.inspection_interval_months", 12));

        TechnicalInspection inspection = TechnicalInspection.builder()
                .vehicle(vehicle)
                .center(request.partnerId() == null ? null : findCenter(request.partnerId()))
                .inspectionDate(request.inspectionDate())
                .nextInspectionDate(next)
                .result(request.result())
                .defectsNoted(request.defectsNoted())
                .cost(request.cost() == null ? java.math.BigDecimal.ZERO : request.cost())
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        TechnicalInspection saved = repository.save(inspection);

        if (request.result().blocksVehicle()) {
            log.warn("Visite technique NON CONFORME pour {} — le camion est bloque a l'affectation",
                    vehicle.getRegistrationNumber());
        }

        log.info("Visite technique enregistree pour {} par {} — resultat : {}",
                vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail(), request.result());

        return TechnicalInspectionResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public TechnicalInspectionResponse update(Long id, TechnicalInspectionRequest request) {
        TechnicalInspection inspection = find(id);

        EditWindowGuard.assertEditable(inspection.getCreatedAt(),
                settingService.getInt("inspection.edit_window_hours", 24), "RG-8-EDIT", "Cette visite technique");

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        LocalDate next = request.nextInspectionDate() != null
                ? request.nextInspectionDate()
                : request.inspectionDate().plusMonths(
                        settingService.getInt("compliance.inspection_interval_months", 12));

        inspection.setVehicle(vehicle);
        inspection.setCenter(request.partnerId() == null ? null : findCenter(request.partnerId()));
        inspection.setInspectionDate(request.inspectionDate());
        inspection.setNextInspectionDate(next);
        inspection.setResult(request.result());
        inspection.setDefectsNoted(request.defectsNoted());
        inspection.setCost(request.cost() == null ? java.math.BigDecimal.ZERO : request.cost());

        log.info("Visite technique de {} corrigee par {}", vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());
        return TechnicalInspectionResponse.from(inspection);
    }

    /** Visites arrivant a echeance, pour la tache planifiee d'alerte. */
    @Transactional(readOnly = true)
    public List<TechnicalInspection> findExpiringBefore(LocalDate limit) {
        return repository.findByNextInspectionDateLessThanEqual(limit);
    }

    private Partner findCenter(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Centre de controle", id));
    }

    /**
     * Point d'entree unique pour charger une visite technique par id —
     * centralise ici la restriction de ville (RG-13.4). 404, jamais
     * 403, pour ne pas confirmer que l'id existe ailleurs.
     */
    private TechnicalInspection find(Long id) {
        TechnicalInspection inspection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Visite technique", id));
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        if (cityId != null && (inspection.getVehicle() == null || inspection.getVehicle().getCity() == null
                || !cityId.equals(inspection.getVehicle().getCity().getId()))) {
            throw new ResourceNotFoundException("Visite technique", id);
        }
        return inspection;
    }

    /** Vrai si le camion est dans la ville geree, ou si l'appelant voit tout (admin). */
    private boolean inScope(Long vehicleId) {
        return SecurityUtils.currentCityId()
                .map(cityId -> vehicleRepository.findById(vehicleId)
                        .map(v -> v.getCity() != null && cityId.equals(v.getCity().getId()))
                        .orElse(false))
                .orElse(true);
    }

    /** Un chauffeur en saisie libre (SELF_MANAGE) ne peut viser que le camion qui lui est actuellement affecte. */
    private void assertOwnVehicle(Long vehicleId) {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED",
                        "Aucun dossier chauffeur associe a ce compte", HttpStatus.FORBIDDEN));
        VehicleAssignment assignment = assignmentRepository.findByDriverIdAndEndDateIsNull(driver.getId()).orElse(null);
        if (assignment == null || !assignment.getVehicle().getId().equals(vehicleId)) {
            throw new BusinessException("ACCESS_DENIED",
                    "Vous ne pouvez saisir que pour le camion qui vous est actuellement affecte", HttpStatus.FORBIDDEN);
        }
    }
}
