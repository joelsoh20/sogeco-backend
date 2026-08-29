package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceItemType;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Lavage hebdomadaire automatique des camions en tour de ville.
 *
 * Un camion affecte au tour de ville (Vehicle.usageType == TOUR_VILLE)
 * a droit a un lavage chaque samedi — deduit comme charge de
 * maintenance (categorie LAVERIE), au montant fixe par camion
 * (weeklyWashCost, 2500 ou 3000 FCFA, saisi a la fiche camion).
 *
 * Tourne sans utilisateur authentifie : passe par le repository
 * directement plutot que par MaintenanceService.create(), qui exige
 * MAINTENANCE_CREATE en contexte de securite (meme choix que
 * ReportScheduler pour les exports planifies).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LaverieScheduler {

    private final VehicleRepository vehicleRepository;
    private final MaintenanceLogRepository maintenanceLogRepository;

    @Scheduled(cron = "0 0 6 * * SAT", zone = "Africa/Douala")
    @Transactional
    public void deduireLavageHebdomadaire() {
        LocalDate today = LocalDate.now();
        int created = 0;

        for (Vehicle vehicle : vehicleRepository.findByActiveTrueAndUsageType(UsageType.TOUR_VILLE)) {
            if (vehicle.getWeeklyWashCost() == null) {
                log.warn("Camion {} en tour de ville sans frais de laverie defini — lavage du {} ignore",
                        vehicle.getRegistrationNumber(), today);
                continue;
            }

            if (maintenanceLogRepository.existsByVehicleIdAndCategoryAndInterventionDate(
                    vehicle.getId(), MaintenanceCategory.LAVERIE, today)) {
                continue;
            }

            MaintenanceLog entry = MaintenanceLog.builder()
                    .vehicle(vehicle)
                    .category(MaintenanceCategory.LAVERIE)
                    .description("Lavage hebdomadaire (tour de ville)")
                    .interventionDate(today)
                    .completionDate(today)
                    .odometerKm(vehicle.getCurrentKilometers())
                    .status(MaintenanceStatus.TERMINEE)
                    .build();

            entry.addItem(MaintenanceItem.builder()
                    .itemType(MaintenanceItemType.MAIN_OEUVRE)
                    .label("Lavage")
                    .unitPrice(vehicle.getWeeklyWashCost())
                    .build());
            entry.recomputeCosts();

            maintenanceLogRepository.save(entry);
            created++;
        }

        if (created > 0) {
            log.info("{} lavage(s) hebdomadaire(s) enregistre(s) pour le {}", created, today);
        }
    }
}
