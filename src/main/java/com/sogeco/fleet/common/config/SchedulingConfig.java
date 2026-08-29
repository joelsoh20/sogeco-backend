package com.sogeco.fleet.common.config;

import com.sogeco.fleet.modules.document.DocumentService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Taches planifiees du sprint 2.
 *
 * Les taches liees a la telematique et aux alertes arriveront au
 * sprint 5, celles des rapports au sprint 7.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulingConfig {

    private final DocumentService documentService;
    private final VehicleRepository vehicleRepository;

    /**
     * Recalcule les statuts d'echeance documentaire (RG-8.2).
     * A 06h00, avant l'arrivee des gestionnaires.
     */
    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Douala")
    public void refreshDocumentStatuses() {
        int count = documentService.refreshAllStatuses();
        log.info("Tache quotidienne : {} statuts documentaires recalcules", count);
    }

    /**
     * Remise a zero du kilometrage du jour, affiche sur la liste des
     * camions et la carte GPS (RG-3.8).
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Africa/Douala")
    @Transactional
    public void resetDailyKilometers() {
        List<Vehicle> vehicles = vehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc();
        vehicles.forEach(Vehicle::resetDailyKm);
        log.info("Tache quotidienne : kilometrage du jour remis a zero pour {} camions", vehicles.size());
    }
}
