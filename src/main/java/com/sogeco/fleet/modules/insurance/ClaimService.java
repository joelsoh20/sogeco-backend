package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.ClaimStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.dto.ClaimDecisionRequest;
import com.sogeco.fleet.modules.insurance.dto.ClaimRequest;
import com.sogeco.fleet.modules.insurance.dto.ClaimResponse;
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

import java.time.Year;

/**
 * Sinistres.
 *
 * Regles couvertes : RG-8.7 a RG-8.9. Le rattachement a une police et
 * a une intervention de maintenance est facultatif : tous les
 * sinistres ne sont pas declares a l'assureur, toutes les reparations
 * ne decoulent pas d'un sinistre.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository repository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final InsurancePolicyRepository policyRepository;
    private final UserRepository userRepository;
    private final SettingService settingService;
    private final VehicleAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<ClaimResponse> list(Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));
        return PageResponse.from(page, ClaimResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public ClaimResponse get(Long id) {
        return ClaimResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public java.util.List<ClaimResponse> forVehicle(Long vehicleId) {
        if (!inScope(vehicleId)) {
            return java.util.List.of();
        }
        return repository.findByVehicleIdOrderByIncidentDateDesc(vehicleId)
                .stream().map(ClaimResponse::from).toList();
    }

    /** Ce que le chauffeur connecte a lui-meme declare — espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public java.util.List<ClaimResponse> mine() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        return repository.findByCreatedByUserIdOrderByIncidentDateDesc(userId)
                .stream().map(ClaimResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE') or hasAuthority('SELF_MANAGE')")
    public ClaimResponse create(ClaimRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        boolean selfService = !SecurityUtils.hasPermission("INSURANCE_CREATE");
        Driver ownDriver = selfService ? assertOwnVehicleAndGetDriver(vehicle.getId()) : null;

        Claim claim = Claim.builder()
                .claimNumber(nextClaimNumber())
                .vehicle(vehicle)
                .driver(selfService ? ownDriver
                        : request.driverId() == null ? null : driverRepository.findById(request.driverId())
                        .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", request.driverId())))
                .policy(request.insurancePolicyId() == null ? null : policyRepository.findById(request.insurancePolicyId())
                        .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", request.insurancePolicyId())))
                .incidentDate(request.incidentDate())
                .claimType(request.claimType())
                .description(request.description())
                .locationLabel(request.locationLabel())
                .policeReportNumber(request.policeReportNumber())
                .estimatedCost(request.estimatedCost())
                .deductibleAmount(request.deductibleAmount())
                .status(ClaimStatus.DECLARE)
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        Claim saved = repository.save(claim);
        log.info("Sinistre {} declare pour {} par {}",
                saved.getClaimNumber(), vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return ClaimResponse.from(saved);
    }

    /**
     * Correction dans les 24h suivant la declaration (RG-8-EDIT).
     *
     * Passe ce delai, seul un administrateur peut encore corriger le
     * contenu — le statut et le montant rembourse restent du ressort
     * exclusif de decide(), jamais touches ici.
     */
    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public ClaimResponse update(Long id, ClaimRequest request) {
        Claim claim = find(id);

        EditWindowGuard.assertEditable(claim.getCreatedAt(),
                settingService.getInt("claim.edit_window_hours", 24), "RG-8-EDIT", "Ce sinistre");

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        claim.setVehicle(vehicle);
        claim.setDriver(request.driverId() == null ? null : driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", request.driverId())));
        claim.setPolicy(request.insurancePolicyId() == null ? null : policyRepository.findById(request.insurancePolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("Police d'assurance", request.insurancePolicyId())));
        claim.setIncidentDate(request.incidentDate());
        claim.setClaimType(request.claimType());
        claim.setDescription(request.description());
        claim.setLocationLabel(request.locationLabel());
        claim.setPoliceReportNumber(request.policeReportNumber());
        claim.setEstimatedCost(request.estimatedCost());
        claim.setDeductibleAmount(request.deductibleAmount());

        log.info("Sinistre {} corrige par {}", claim.getClaimNumber(), SecurityUtils.currentUserEmail());
        return ClaimResponse.from(claim);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_MANAGE')")
    public ClaimResponse decide(Long id, ClaimDecisionRequest request) {
        Claim claim = find(id);

        if (claim.getStatus() == ClaimStatus.CLOTURE) {
            throw new BusinessException("RG-8.9",
                    "Ce sinistre est deja cloture", HttpStatus.CONFLICT);
        }

        claim.setStatus(request.status());
        if (request.reimbursedAmount() != null) {
            claim.setReimbursedAmount(request.reimbursedAmount());
        }

        log.info("Sinistre {} : statut {}", claim.getClaimNumber(), request.status());
        return ClaimResponse.from(claim);
    }

    /** Un chauffeur en saisie libre (SELF_MANAGE) ne peut declarer que pour le camion qui lui est actuellement affecte. */
    private Driver assertOwnVehicleAndGetDriver(Long vehicleId) {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        Driver driver = driverRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED",
                        "Aucun dossier chauffeur associe a ce compte", HttpStatus.FORBIDDEN));
        VehicleAssignment assignment = assignmentRepository.findByDriverIdAndEndDateIsNull(driver.getId()).orElse(null);
        if (assignment == null || !assignment.getVehicle().getId().equals(vehicleId)) {
            throw new BusinessException("ACCESS_DENIED",
                    "Vous ne pouvez declarer que pour le camion qui vous est actuellement affecte", HttpStatus.FORBIDDEN);
        }
        return driver;
    }

    /**
     * Point d'entree unique pour charger un sinistre par id — centralise
     * ici la restriction de ville (RG-13.4). 404, jamais 403, pour ne
     * pas confirmer que l'id existe ailleurs dans l'entreprise.
     */
    private Claim find(Long id) {
        Claim claim = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sinistre", id));
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        if (cityId != null && (claim.getVehicle() == null || claim.getVehicle().getCity() == null
                || !cityId.equals(claim.getVehicle().getCity().getId()))) {
            throw new ResourceNotFoundException("Sinistre", id);
        }
        return claim;
    }

    /** Vrai si le camion est dans la ville geree, ou si l'appelant voit tout (admin). */
    private boolean inScope(Long vehicleId) {
        return SecurityUtils.currentCityId()
                .map(cityId -> vehicleRepository.findById(vehicleId)
                        .map(v -> v.getCity() != null && cityId.equals(v.getCity().getId()))
                        .orElse(false))
                .orElse(true);
    }

    /** Numero au format SIN-AAAA-NNN, sequence annuelle. */
    private String nextClaimNumber() {
        String prefix = settingService.getString("claim.number_prefix", "SIN");
        int year = Year.now().getValue();
        String pattern = "%s-%d-%%".formatted(prefix, year);
        long count = repository.countByNumberPrefix(pattern);
        return "%s-%d-%03d".formatted(prefix, year, count + 1);
    }
}
