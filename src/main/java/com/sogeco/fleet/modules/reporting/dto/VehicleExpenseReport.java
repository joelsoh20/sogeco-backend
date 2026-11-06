package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Donnees du rapport PDF des depenses par camion, sur une periode.
 *
 * Les depenses sont ventilees par domaine de l'application (carburant,
 * maintenance, laverie, assurance, visite technique, peages, frais de mission,
 * autres frais de mission, depenses diverses). La quote-part salariale des
 * chauffeurs n'y figure pas : meme convention que la repartition des couts de
 * l'ecran Rapports (voir CostCategory).
 */
public record VehicleExpenseReport(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        String generatedBy,
        /** Total toutes depenses confondues, par domaine, pour l'ensemble des camions du rapport. */
        Map<Domain, BigDecimal> fleetExpenses,
        BigDecimal fleetTotalExpenses,
        /** Missions terminees de la periode, par type de mission, pour l'ensemble des camions. */
        List<MissionTypeLine> fleetMissionTypes,
        long fleetMissionCount,
        BigDecimal fleetRevenue,
        BigDecimal fleetKm,
        /** Carburant consomme au garage pour les essais (litres), hors cout : toute la flotte du rapport. */
        BigDecimal fleetTestFuelLiters,
        /** Jours passes en panne (reparations de la periode), toute la flotte du rapport. */
        long fleetDowntimeDays,
        List<VehicleSection> vehicles
) {

    /** Domaines de depense, dans l'ordre d'affichage. */
    public enum Domain {
        CARBURANT("Carburant"),
        MAINTENANCE("Maintenance et reparations"),
        LAVERIE("Laverie"),
        ASSURANCE("Assurance"),
        VISITE_TECHNIQUE("Visite technique"),
        PEAGES("Peages"),
        FRAIS_MISSION("Frais de mission"),
        AUTRES_FRAIS_MISSION("Autres frais de mission"),
        DEPENSES_DIVERSES("Depenses diverses");

        private final String label;

        Domain(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record MissionTypeLine(
            String typeLabel,
            long missionCount,
            BigDecimal distanceKm,
            BigDecimal revenue,
            BigDecimal cost
    ) {
    }

    public record VehicleSection(
            Long vehicleId,
            String registrationNumber,
            String description,
            String cityName,
            Map<Domain, BigDecimal> expenses,
            BigDecimal totalExpenses,
            List<MissionTypeLine> missionTypes,
            long missionCount,
            BigDecimal distanceKm,
            BigDecimal revenue,
            BigDecimal missionMargin,
            /** Depenses totales / km parcourus en mission ; null sans distance. */
            BigDecimal expensePerKm,
            /** Carburant consomme au garage pour les essais (litres) sur la periode. */
            BigDecimal testFuelLiters,
            /** Jours passes en panne sur la periode (somme des reparations). */
            long downtimeDays
    ) {
    }
}
