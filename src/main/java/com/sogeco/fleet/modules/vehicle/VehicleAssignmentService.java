package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.dto.AssignmentRequest;
import com.sogeco.fleet.modules.vehicle.dto.AssignmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Affectation d'un chauffeur a un camion.
 *
 * Les controles applicatifs donnent des messages clairs, mais ce sont
 * les index uniques partiels de la base qui garantissent reellement
 * l'unicite : deux requetes simultanees ne peuvent pas creer deux
 * affectations actives sur le meme camion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehicleAssignmentService {

    private final VehicleAssignmentRepository repository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final UserRepository userRepository;
    private final VehicleService vehicleService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VEHICLE_READ')")
    public List<AssignmentResponse> historyForVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByStartDateDesc(vehicleId)
                .stream().map(AssignmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DRIVER_READ')")
    public List<AssignmentResponse> historyForDriver(Long driverId) {
        return repository.findByDriverIdOrderByStartDateDesc(driverId)
                .stream().map(AssignmentResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    public AssignmentResponse assign(Long vehicleId, AssignmentRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", vehicleId));
        Driver driver = driverRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", request.driverId()));

        // Conformite du camion : assurance, visite technique, carte grise
        vehicleService.assertAssignable(vehicleId);

        // Etat du chauffeur : actif (RG-9.2). Le permis expire n'est plus bloquant.
        if (!driver.isAssignable()) {
            throw new BusinessException("RG-9.3",
                    "Ce chauffeur ne peut pas etre affecte : son statut est " + driver.getStatus(),
                    HttpStatus.CONFLICT);
        }

        repository.findByVehicleIdAndEndDateIsNull(vehicleId).ifPresent(existing -> {
            throw new BusinessException("RG-9.4",
                    "Ce camion est deja affecte a %s. Liberez l'affectation d'abord."
                            .formatted(existing.getDriver().getFullName()),
                    HttpStatus.CONFLICT);
        });

        repository.findByDriverIdAndEndDateIsNull(request.driverId()).ifPresent(existing -> {
            throw new BusinessException("RG-9.5",
                    "Ce chauffeur conduit deja le camion %s"
                            .formatted(existing.getVehicle().getRegistrationNumber()),
                    HttpStatus.CONFLICT);
        });

        VehicleAssignment assignment = VehicleAssignment.builder()
                .vehicle(vehicle)
                .driver(driver)
                .startDate(request.startDate() == null ? LocalDate.now() : request.startDate())
                .assignedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .notes(request.notes())
                .build();

        try {
            VehicleAssignment saved = repository.saveAndFlush(assignment);
            log.info("Chauffeur {} affecte au camion {} par {}",
                    driver.getFullName(), vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());
            return AssignmentResponse.from(saved);

        } catch (DataIntegrityViolationException e) {
            // Course entre deux requetes : l'index unique partiel a tranche.
            throw new BusinessException("RG-9.4",
                    "Une affectation concurrente vient d'etre creee, rechargez la page",
                    HttpStatus.CONFLICT);
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('VEHICLE_UPDATE')")
    public void release(Long vehicleId) {
        VehicleAssignment assignment = repository.findByVehicleIdAndEndDateIsNull(vehicleId)
                .orElseThrow(() -> new BusinessException("RG-9.4",
                        "Aucune affectation en cours pour ce camion", HttpStatus.NOT_FOUND));

        assignment.close(LocalDate.now());
        log.info("Affectation du camion {} liberee par {}",
                assignment.getVehicle().getRegistrationNumber(), SecurityUtils.currentUserEmail());
    }
}
