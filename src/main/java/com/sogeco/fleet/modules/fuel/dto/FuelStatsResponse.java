package com.sogeco.fleet.modules.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Compteurs de tete de l'ecran Carburant. */
public record FuelStatsResponse(
        BigDecimal coutTotal,
        BigDecimal litresConsommes,
        BigDecimal consommationMoyenne,
        BigDecimal coutMoyenParKm,
        BigDecimal kilometresParcourus,
        long nombreAnomalies,
        List<VehicleFuelBreakdown> repartitionParCamion,
        List<MonthlyConsumption> consommationSixMois
) {
    public record VehicleFuelBreakdown(
            Long vehicleId,
            String registrationNumber,
            BigDecimal litres,
            BigDecimal cout,
            BigDecimal consommationMoyenne,
            BigDecimal partPourcent
    ) {
    }

    /** Point de la courbe "Consommation" — 6 derniers mois, premier jour du mois. */
    public record MonthlyConsumption(LocalDate month, BigDecimal litres) {
    }
}
