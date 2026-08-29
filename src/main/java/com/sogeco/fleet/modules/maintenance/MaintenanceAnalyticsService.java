package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.maintenance.dto.MaintenanceCityStatsResponse;
import com.sogeco.fleet.modules.maintenance.dto.MaintenanceStatsResponse;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Agregats de l'ecran Maintenance et Pannes. */
@Service
@RequiredArgsConstructor
public class MaintenanceAnalyticsService {

    private final MaintenanceLogRepository repository;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public MaintenanceStatsResponse stats(LocalDate from, LocalDate to) {

        BigDecimal totalCost = repository.totalCost(from, to);

        List<MaintenanceStatsResponse.CategoryBreakdown> categories = new ArrayList<>();
        long interventions = 0;
        long preventive = 0;

        for (Object[] row : repository.aggregateByCategory(from, to)) {
            MaintenanceCategory category = (MaintenanceCategory) row[0];
            long count = (Long) row[1];
            BigDecimal amount = (BigDecimal) row[2];

            interventions += count;
            if (category.isPreventive()) {
                preventive += count;
            }

            BigDecimal share = totalCost.signum() == 0
                    ? BigDecimal.ZERO
                    : amount.multiply(BigDecimal.valueOf(100))
                        .divide(totalCost, 1, RoundingMode.HALF_UP);

            categories.add(new MaintenanceStatsResponse.CategoryBreakdown(
                    category, count, amount, share));
        }

        List<MaintenanceStatsResponse.DailyCost> trend = new ArrayList<>();
        for (Object[] row : repository.aggregateByDate(from, to)) {
            trend.add(new MaintenanceStatsResponse.DailyCost((LocalDate) row[0], (BigDecimal) row[1]));
        }

        List<MaintenanceStatsResponse.GarageComparison> garages = new ArrayList<>();
        for (Object[] row : repository.aggregateByGarage(from, to)) {
            long count = (Long) row[2];
            long recurrences = ((Number) row[5]).longValue();

            garages.add(new MaintenanceStatsResponse.GarageComparison(
                    (Long) row[0], (String) row[1], count,
                    (BigDecimal) row[3],
                    ((BigDecimal) row[4]).setScale(2, RoundingMode.HALF_UP),
                    recurrences,
                    count == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(recurrences * 100.0 / count)
                                .setScale(1, RoundingMode.HALF_UP)));
        }

        // Indicateur de maturite : une flotte bien geree tend vers 70 %.
        BigDecimal preventiveRate = interventions == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(preventive * 100.0 / interventions)
                    .setScale(1, RoundingMode.HALF_UP);

        BigDecimal averageCost = interventions == 0
                ? BigDecimal.ZERO
                : totalCost.divide(BigDecimal.valueOf(interventions), 2, RoundingMode.HALF_UP);

        return new MaintenanceStatsResponse(
                totalCost,
                interventions,
                vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.EN_MAINTENANCE),
                repository.countByIsBreakdownTrueAndInterventionDateBetween(from, to),
                repository.findByStatusAndInterventionDateBetween(
                        MaintenanceStatus.PLANIFIEE, LocalDate.now(), LocalDate.now().plusDays(7)).size(),
                preventiveRate,
                averageCost,
                categories,
                garages,
                trend);
    }

    /** Une ligne par ville d'affectation des camions, pour le bouton "Statistiques par ville". */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public List<MaintenanceCityStatsResponse> statsByCity(LocalDate from, LocalDate to) {
        List<MaintenanceCityStatsResponse> results = new ArrayList<>();
        for (Object[] row : repository.aggregateByCity(from, to)) {
            results.add(new MaintenanceCityStatsResponse(
                    (Long) row[0], (String) row[1], (Long) row[2], (BigDecimal) row[3],
                    ((Number) row[4]).longValue()));
        }
        return results;
    }
}
