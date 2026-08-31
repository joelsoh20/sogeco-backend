package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.dto.CarteGriseRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteGriseResponse;
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

/** Cartes grises — un document d'immatriculation par camion. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarteGriseService {

    private final CarteGriseRepository repository;
    private final VehicleRepository vehicleRepository;
    private final SettingService settingService;
    private final DriverRepository driverRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public PageResponse<CarteGriseResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), CarteGriseResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<CarteGriseResponse> forVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByExpiryDateDesc(vehicleId)
                .stream().map(CarteGriseResponse::from).toList();
    }

    /** Ce que le chauffeur connecte a lui-meme saisi — espace chauffeur. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('SELF_READ')")
    public List<CarteGriseResponse> mine() {
        Long userId = SecurityUtils.currentUserId()
                .orElseThrow(() -> new BusinessException("ACCESS_DENIED", "Non authentifie", HttpStatus.FORBIDDEN));
        return repository.findByCreatedByUserIdOrderByExpiryDateDesc(userId)
                .stream().map(CarteGriseResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_CREATE') or hasAuthority('SELF_MANAGE')")
    public CarteGriseResponse create(CarteGriseRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        if (!SecurityUtils.hasPermission("INSURANCE_CREATE")) {
            assertOwnVehicle(vehicle.getId());
        }

        LocalDate expiry = request.expiryDate() != null
                ? request.expiryDate()
                : request.issueDate().plusYears(
                        settingService.getInt("compliance.carte_grise_validity_years", 10));

        CarteGrise carte = CarteGrise.builder()
                .vehicle(vehicle)
                .registrationNumber(request.registrationNumber())
                .chassisNumber(request.chassisNumber())
                .brand(request.brand())
                .genre(request.genre())
                .bodyType(request.bodyType())
                .seatCount(request.seatCount())
                .firstCirculationDate(request.firstCirculationDate())
                .issueDate(request.issueDate())
                .expiryDate(expiry)
                .cost(request.cost())
                .notes(request.notes())
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        CarteGrise saved = repository.save(carte);

        log.info("Carte grise enregistree pour {} par {}",
                vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return CarteGriseResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('INSURANCE_UPDATE')")
    public CarteGriseResponse update(Long id, CarteGriseRequest request) {
        CarteGrise carte = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Carte grise", id));

        EditWindowGuard.assertEditable(carte.getCreatedAt(),
                settingService.getInt("carte_grise.edit_window_hours", 24), "RG-CG-EDIT", "Cette carte grise");

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId()));

        LocalDate expiry = request.expiryDate() != null
                ? request.expiryDate()
                : request.issueDate().plusYears(
                        settingService.getInt("compliance.carte_grise_validity_years", 10));

        carte.setVehicle(vehicle);
        carte.setRegistrationNumber(request.registrationNumber());
        carte.setChassisNumber(request.chassisNumber());
        carte.setBrand(request.brand());
        carte.setGenre(request.genre());
        carte.setBodyType(request.bodyType());
        carte.setSeatCount(request.seatCount());
        carte.setFirstCirculationDate(request.firstCirculationDate());
        carte.setIssueDate(request.issueDate());
        carte.setExpiryDate(expiry);
        carte.setCost(request.cost());
        carte.setNotes(request.notes());

        log.info("Carte grise de {} corrigee par {}", vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());
        return CarteGriseResponse.from(carte);
    }

    /** Cartes grises arrivant a echeance (10 ans par defaut), pour l'echeancier unifie. */
    @Transactional(readOnly = true)
    public List<CarteGrise> findExpiringBefore(LocalDate limit) {
        return repository.findByExpiryDateLessThanEqual(limit);
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
