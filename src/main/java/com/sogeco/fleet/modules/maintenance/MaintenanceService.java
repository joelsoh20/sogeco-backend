package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.EditWindowGuard;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.document.DocumentRepository;
import com.sogeco.fleet.modules.maintenance.dto.*;
import com.sogeco.fleet.modules.partner.PartnerRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Interventions de maintenance.
 *
 * Regles couvertes : RG-7.1 a RG-7.10.
 *
 * Le ratio preventif / curatif est l'indicateur de maturite du module :
 * sa progression mesure directement le benefice du projet sur ce poste.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceService {

    private final MaintenanceLogRepository repository;
    private final VehicleRepository vehicleRepository;
    private final PartnerRepository partnerRepository;
    private final DocumentRepository documentRepository;
    private final SettingService settingService;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public PageResponse<MaintenanceResponse> list(Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findAllByVehicle_City_Id(cityId, pageable))
                .orElseGet(() -> repository.findAllBy(pageable));
        return PageResponse.from(page, log -> MaintenanceResponse.from(log, false));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public MaintenanceResponse get(Long id) {
        return MaintenanceResponse.from(repository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention", id)), true);
    }

    /** Camion d'une autre ville : liste vide, jamais une erreur qui revelerait son existence. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public List<MaintenanceResponse> forVehicle(Long vehicleId) {
        return repository.findByVehicleIdOrderByInterventionDateDesc(vehicleId).stream()
                .filter(this::inCurrentScope)
                .map(log -> MaintenanceResponse.from(log, false)).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public List<MaintenanceResponse> planned() {
        List<MaintenanceLog> logs = SecurityUtils.currentCityId()
                .map(cityId -> repository.findByStatusAndVehicle_City_IdOrderByInterventionDateAsc(
                        MaintenanceStatus.PLANIFIEE, cityId))
                .orElseGet(() -> repository.findByStatusOrderByInterventionDateAsc(MaintenanceStatus.PLANIFIEE));
        return logs.stream().map(log -> MaintenanceResponse.from(log, false)).toList();
    }

    /** Vrai si le camion de l'intervention est dans la ville geree, ou si l'appelant voit tout (admin). */
    private boolean inCurrentScope(MaintenanceLog log) {
        return SecurityUtils.currentCityId()
                .map(cityId -> log.getVehicle().getCity() != null && cityId.equals(log.getVehicle().getCity().getId()))
                .orElse(true);
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_CREATE')")
    public MaintenanceResponse create(MaintenanceRequest request) {
        Vehicle vehicle = findVehicle(request.vehicleId());

        MaintenanceLog maintenance = MaintenanceLog.builder()
                .vehicle(vehicle)
                .garage(request.partnerId() == null ? null : partnerRepository.findById(request.partnerId())
                        .orElseThrow(() -> new ResourceNotFoundException("Garage", request.partnerId())))
                .category(request.category())
                .description(request.description())
                .interventionDate(request.interventionDate())
                .completionDate(request.completionDate())
                .odometerKm(request.odometerKm() == null ? vehicle.getCurrentKilometers() : request.odometerKm())
                .isBreakdown(Boolean.TRUE.equals(request.isBreakdown()))
                .errorCode(request.errorCode())
                .nextInterventionDate(request.nextInterventionDate())
                .nextInterventionKm(request.nextInterventionKm())
                .invoice(request.invoiceDocumentId() == null ? null
                        : documentRepository.findById(request.invoiceDocumentId()).orElse(null))
                .status(MaintenanceStatus.PLANIFIEE)
                .build();

        if (request.items() != null) {
            request.items().forEach(item -> maintenance.addItem(MaintenanceItem.builder()
                    .itemType(item.itemType())
                    .label(item.label())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .build()));
        }
        maintenance.recomputeCosts();

        detectRecurrence(maintenance, null);

        MaintenanceLog saved = repository.save(maintenance);

        // Le seuil de maintenance preventive du camion suit la derniere
        // intervention : il alimentera les alertes du sprint 5.
        if (request.nextInterventionKm() != null) {
            vehicle.setNextMaintenanceKm(request.nextInterventionKm());
        }
        if (request.nextInterventionDate() != null) {
            vehicle.setNextMaintenanceDate(request.nextInterventionDate());
        }

        checkRenewalThreshold(vehicle);

        log.info("Intervention {} creee sur {} par {}",
                request.category(), vehicle.getRegistrationNumber(), SecurityUtils.currentUserEmail());

        return MaintenanceResponse.from(saved, true);
    }

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_UPDATE')")
    public MaintenanceResponse update(Long id, MaintenanceRequest request) {
        MaintenanceLog maintenance = repository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention", id));

        EditWindowGuard.assertEditable(maintenance.getCreatedAt(),
                settingService.getInt("maintenance.edit_window_hours", 1), "RG-7-EDIT", "Cette intervention");

        if (maintenance.getStatus() == MaintenanceStatus.TERMINEE) {
            throw new BusinessException("RG-7.4",
                    "Une intervention terminee n'est plus modifiable", HttpStatus.CONFLICT);
        }

        maintenance.setCategory(request.category());
        maintenance.setDescription(request.description());
        maintenance.setInterventionDate(request.interventionDate());
        maintenance.setOdometerKm(request.odometerKm());
        maintenance.setIsBreakdown(Boolean.TRUE.equals(request.isBreakdown()));
        maintenance.setErrorCode(request.errorCode());
        maintenance.setNextInterventionDate(request.nextInterventionDate());
        maintenance.setNextInterventionKm(request.nextInterventionKm());

        if (request.items() != null) {
            maintenance.getItems().clear();
            request.items().forEach(item -> maintenance.addItem(MaintenanceItem.builder()
                    .itemType(item.itemType())
                    .label(item.label())
                    .quantity(item.quantity())
                    .unitPrice(item.unitPrice())
                    .build()));
        }
        maintenance.recomputeCosts();
        detectRecurrence(maintenance, id);

        return MaintenanceResponse.from(maintenance, true);
    }

    /**
     * Changement de statut. Une intervention en cours immobilise le
     * camion, sa cloture le libere (RG-7.4).
     */
    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_UPDATE')")
    public MaintenanceResponse changeStatus(Long id, MaintenanceStatus status, LocalDate completionDate) {
        MaintenanceLog maintenance = repository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention", id));
        Vehicle vehicle = maintenance.getVehicle();

        if (status == MaintenanceStatus.EN_COURS) {
            if (vehicle.getStatus() == VehicleStatus.EN_MISSION) {
                throw new BusinessException("RG-7.4",
                        "Ce camion est en mission : terminez la mission avant l'intervention",
                        HttpStatus.CONFLICT);
            }
            vehicle.setStatus(VehicleStatus.EN_MAINTENANCE);
            maintenance.setStatus(status);

        } else if (status == MaintenanceStatus.TERMINEE) {
            maintenance.complete(completionDate);
            if (vehicle.getStatus() == VehicleStatus.EN_MAINTENANCE) {
                vehicle.setStatus(VehicleStatus.DISPONIBLE);
            }

        } else {
            maintenance.setStatus(status);
            if (status == MaintenanceStatus.ANNULEE
                    && vehicle.getStatus() == VehicleStatus.EN_MAINTENANCE) {
                vehicle.setStatus(VehicleStatus.DISPONIBLE);
            }
        }

        return MaintenanceResponse.from(maintenance, true);
    }

    // ------------------------------------------------------------------
    // Regles
    // ------------------------------------------------------------------

    /**
     * Retour en atelier : meme camion, meme categorie, sous la fenetre
     * parametree. Indicateur d'une reparation mal faite (RG-7.6).
     */
    private void detectRecurrence(MaintenanceLog maintenance, Long excludeId) {
        int days = settingService.getInt("maintenance.recurrence_days", 30);
        long previous = repository.countRecent(
                maintenance.getVehicle().getId(),
                maintenance.getCategory(),
                maintenance.getInterventionDate().minusDays(days),
                excludeId);

        boolean recurrence = previous > 0;
        maintenance.setIsRecurrence(recurrence);

        if (recurrence) {
            log.warn("Retour en atelier detecte : {} sur {} sous {} jours",
                    maintenance.getCategory(),
                    maintenance.getVehicle().getRegistrationNumber(), days);
        }
    }

    /**
     * Camion candidat au renouvellement : cumul de maintenance sur 12
     * mois depassant une part parametree de sa valeur d'acquisition
     * (RG-7.10).
     */
    private void checkRenewalThreshold(Vehicle vehicle) {
        BigDecimal purchasePrice = vehicle.getPurchasePrice();
        if (purchasePrice == null || purchasePrice.signum() == 0) {
            return;
        }

        int ratio = settingService.getInt("maintenance.renewal_cost_ratio", 40);
        BigDecimal threshold = purchasePrice.multiply(BigDecimal.valueOf(ratio))
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);

        BigDecimal yearCost = repository.totalCostForVehicle(
                vehicle.getId(), LocalDate.now().minusYears(1), LocalDate.now());

        if (yearCost.compareTo(threshold) > 0) {
            log.warn("Camion {} candidat au renouvellement : {} FCFA de maintenance sur 12 mois, "
                            + "soit plus de {}% de sa valeur d'acquisition",
                    vehicle.getRegistrationNumber(), yearCost, ratio);
        }
    }

    private Vehicle findVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", id));
    }
}
