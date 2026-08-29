package com.sogeco.fleet.modules.maintenance.dto;

import com.sogeco.fleet.common.enums.MaintenanceCategory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Compteurs de tete de l'ecran Maintenance et Pannes. */
public record MaintenanceStatsResponse(
        BigDecimal coutTotal,
        long interventions,
        long camionsEnMaintenance,
        long pannes,
        long interventionsAVenir,
        BigDecimal tauxPreventif,
        BigDecimal coutMoyenParIntervention,
        List<CategoryBreakdown> repartitionParCategorie,
        List<GarageComparison> comparatifGarages,
        List<DailyCost> tendanceCouts
) {
    public record CategoryBreakdown(MaintenanceCategory category, long count,
                                    BigDecimal amount, BigDecimal sharePercent) {
    }

    public record GarageComparison(Long garageId, String garageName, long interventions,
                                   BigDecimal totalCost, BigDecimal averageCost,
                                   long recurrences, BigDecimal recurrenceRate) {
    }

    /** Point de la courbe "Couts de maintenance" de la maquette. */
    public record DailyCost(LocalDate date, BigDecimal amount) {
    }
}
