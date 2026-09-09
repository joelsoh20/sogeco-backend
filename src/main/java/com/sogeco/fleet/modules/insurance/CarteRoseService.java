package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.dto.CarteRoseRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteRoseResponse;
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

/** Cartes roses CEMAC — une attestation d'assurance par camion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarteRoseService {

    private final CarteRoseRepository repository;
    private final VehicleRepository vehicleRepository;
    private final PartnerRepository partnerRepository;
    private final SettingService settingService;
    private final DriverRepository driverRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<CarteRoseResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), CarteRoseResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public CarteRoseResponse get(Long id) {
        return CarteRoseResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carte rose", id)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<CarteRoseResponse> forVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByValidToDesc(vehicleId)
                .stream().map(CarteRoseResponse::from).toList();
    }

    /** Ce que le chauffeur connecte a lui-meme saisi — espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public List<CarteRoseResponse> mine() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        return repository.findByCreatedByUserIdOrderByValidToDesc(userId)
                .stream().map(CarteRoseResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE') or hasAuthority('SELF_MANAGE')")
    public CarteRoseResponse create(CarteRoseRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        if (!SecurityUtils.hasPermission("INSURANCE_CREATE")) {
            assertOwnVehicle(vehicle.getId());
        }

        Partner insurer = findInsurer(request.insurerId());
        LocalDate validTo = resolveValidTo(request.validFrom(), request.validTo());

        CarteRose carte = CarteRose.builder()
                .vehicle(vehicle)
                .insurer(insurer)
                .insuredName(request.insuredName())
                .insuredAddress(request.insuredAddress())
                .issuingBureauName(request.issuingBureauName())
                .issuingBureauAddress(request.issuingBureauAddress())
                .registrationNumber(request.registrationNumber())
                .vehicleMakeType(request.vehicleMakeType())
                .vehicleCategory(request.vehicleCategory())
                .validFrom(request.validFrom())
                .validTo(validTo)
                .cost(request.cost())
                .notes(request.notes())
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        CarteRose saved = repository.save(carte);

        log.info("Carte rose enregistree pour {} par {}",
                vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return CarteRoseResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public CarteRoseResponse update(Long id, CarteRoseRequest request) {
        CarteRose carte = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carte rose", id));

        EditWindowGuard.assertEditable(carte.getCreatedAt(),
                settingService.getInt("carte_rose.edit_window_hours", 24), "RG-CR-EDIT", "Cette carte rose");

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));
        Partner insurer = findInsurer(request.insurerId());
        LocalDate validTo = resolveValidTo(request.validFrom(), request.validTo());

        carte.setVehicle(vehicle);
        carte.setInsurer(insurer);
        carte.setInsuredName(request.insuredName());
        carte.setInsuredAddress(request.insuredAddress());
        carte.setIssuingBureauName(request.issuingBureauName());
        carte.setIssuingBureauAddress(request.issuingBureauAddress());
        carte.setRegistrationNumber(request.registrationNumber());
        carte.setVehicleMakeType(request.vehicleMakeType());
        carte.setVehicleCategory(request.vehicleCategory());
        carte.setValidFrom(request.validFrom());
        carte.setValidTo(validTo);
        carte.setCost(request.cost());
        carte.setNotes(request.notes());

        log.info("Carte rose de {} corrigee par {}", vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());
        return CarteRoseResponse.from(carte);
    }

    /** Cartes roses arrivant a echeance (1 an par defaut), pour l'echeancier unifie. */
    @Transactional(readOnly = true)
    public List<CarteRose> findExpiringBefore(LocalDate limit) {
        return repository.findByValidToLessThanEqual(limit);
    }

    private LocalDate resolveValidTo(LocalDate validFrom, LocalDate requestedValidTo) {
        return requestedValidTo != null
                ? requestedValidTo
                : validFrom.plusYears(settingService.getInt("compliance.carte_rose_validity_years", 1));
    }

    private Partner findInsurer(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Assureur", id));
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
