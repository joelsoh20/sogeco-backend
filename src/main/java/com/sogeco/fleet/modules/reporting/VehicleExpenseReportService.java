package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.insurance.InsurancePolicy;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.Domain;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.MissionTypeLine;
import com.sogeco.fleet.modules.reporting.dto.VehicleExpenseReport.VehicleSection;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rassemble, camion par camion, les depenses de chaque domaine de l'application
 * et le bilan des missions par type, pour le rapport PDF.
 *
 * Sources et conventions (alignees sur l'ecran Rapports) :
 *  - missions : missions TERMINEES dont la cloture tombe dans la periode ;
 *    carburant, peages, frais de mission et autres frais viennent de leurs
 *    colonnes de cout. La quote-part salariale du chauffeur est exclue ;
 *  - carburant : celui impute aux missions + les pleins hors mission (ceux
 *    rattaches a une mission sont deja dans le cout de la mission, les
 *    recompter ferait doublon) ;
 *  - maintenance et laverie : interventions non annulees, par date d'intervention ;
 *  - assurance : prime des polices demarrant dans la periode, repartie a parts
 *    egales entre les camions qu'elles couvrent ;
 *  - visite technique : cout des visites realisees dans la periode ;
 *  - depenses diverses : depenses enregistrees pour le camion, hors celles deja
 *    imputees au cout direct d'une mission (peage, manutention, amende).
 *
 * Perimetre restreint a la ville du gestionnaire non-administrateur (RG-13.4).
 */
@Service
@RequiredArgsConstructor
public class VehicleExpenseReportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final String ENUMS = "com.sogeco.fleet.common.enums.";

    private final EntityManager em;
    private final VehicleRepository vehicleRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public VehicleExpenseReport build(LocalDate from, LocalDate to, java.util.Collection<Long> vehicleIds) {
        if (to.isBefore(from)) {
            throw new BusinessException("REPORT_PERIOD",
                    "La date de fin precede la date de debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        Long cityId = SecurityUtils.currentCityId().orElse(null);

        List<Vehicle> vehicles = vehicleRepository.findActiveForCity(cityId).stream()
                .filter(v -> vehicleIds == null || vehicleIds.isEmpty() || vehicleIds.contains(v.getId()))
                .toList();
        if (vehicleIds != null && !vehicleIds.isEmpty() && vehicles.isEmpty()) {
            throw new BusinessException("REPORT_VEHICLE",
                    "Camion introuvable dans votre perimetre", HttpStatus.NOT_FOUND);
        }

        Map<Long, Map<Domain, BigDecimal>> expenses = new HashMap<>();

        // --- Missions : composantes de cout par camion -------------------------------
        for (Object[] r : rows("""
                SELECT m.vehicle.id, COALESCE(SUM(m.fuelCost),0), COALESCE(SUM(m.tollCost),0),
                       COALESCE(SUM(m.missionFeeCost),0), COALESCE(SUM(m.otherCost),0)
                FROM Mission m
                WHERE m.status = %sMissionStatus.TERMINEE
                  AND m.actualEnd >= :from AND m.actualEnd < :to
                GROUP BY m.vehicle.id""".formatted(ENUMS), "from", start, "to", end)) {
            Long id = (Long) r[0];
            add(expenses, id, Domain.CARBURANT, (BigDecimal) r[1]);
            add(expenses, id, Domain.PEAGES, (BigDecimal) r[2]);
            add(expenses, id, Domain.FRAIS_MISSION, (BigDecimal) r[3]);
            add(expenses, id, Domain.AUTRES_FRAIS_MISSION, (BigDecimal) r[4]);
        }

        // --- Carburant hors mission -------------------------------------------------------
        for (Object[] r : rows("""
                SELECT f.vehicle.id, COALESCE(SUM(f.totalCost),0)
                FROM FuelLog f
                WHERE f.status <> %sFuelLogStatus.ANNULE AND f.mission IS NULL
                  AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
                GROUP BY f.vehicle.id""".formatted(ENUMS), "from", start, "to", end)) {
            add(expenses, (Long) r[0], Domain.CARBURANT, (BigDecimal) r[1]);
        }

        // --- Maintenance / laverie ------------------------------------------------------
        for (Object[] r : rows("""
                SELECT m.vehicle.id, m.category, COALESCE(SUM(m.totalCost),0)
                FROM MaintenanceLog m
                WHERE m.status <> %sMaintenanceStatus.ANNULEE
                  AND m.interventionDate >= :from AND m.interventionDate <= :to
                GROUP BY m.vehicle.id, m.category""".formatted(ENUMS), "from", from, "to", to)) {
            boolean laverie = r[1] == com.sogeco.fleet.common.enums.MaintenanceCategory.LAVERIE;
            add(expenses, (Long) r[0], laverie ? Domain.LAVERIE : Domain.MAINTENANCE, (BigDecimal) r[2]);
        }

        // --- Visites techniques -----------------------------------------------------------
        for (Object[] r : rows("""
                SELECT i.vehicle.id, COALESCE(SUM(i.cost),0)
                FROM TechnicalInspection i
                WHERE i.inspectionDate >= :from AND i.inspectionDate <= :to
                GROUP BY i.vehicle.id""", "from", from, "to", to)) {
            add(expenses, (Long) r[0], Domain.VISITE_TECHNIQUE, (BigDecimal) r[1]);
        }

        // --- Assurance : prime repartie entre les camions couverts ------------------
        List<InsurancePolicy> policies = em.createQuery("""
                SELECT DISTINCT p FROM InsurancePolicy p JOIN FETCH p.vehicles
                WHERE p.startDate >= :from AND p.startDate <= :to""", InsurancePolicy.class)
                .setParameter("from", from).setParameter("to", to).getResultList();
        for (InsurancePolicy policy : policies) {
            if (policy.getPremiumAmount() == null || policy.getVehicles().isEmpty()) {
                continue;
            }
            BigDecimal share = policy.getPremiumAmount()
                    .divide(BigDecimal.valueOf(policy.getVehicles().size()), 2, RoundingMode.HALF_UP);
            for (Vehicle covered : policy.getVehicles()) {
                add(expenses, covered.getId(), Domain.ASSURANCE, share);
            }
        }

        // --- Depenses diverses (hors cout direct deja imputee a une mission) ------
        for (Object[] r : rows("""
                SELECT e.vehicle.id, COALESCE(SUM(e.amount),0)
                FROM Expense e
                WHERE e.vehicle IS NOT NULL
                  AND e.expenseDate >= :from AND e.expenseDate <= :to
                  AND NOT (e.mission IS NOT NULL AND e.category IN (
                        %1$sExpenseCategory.PEAGE, %1$sExpenseCategory.MANUTENTION, %1$sExpenseCategory.AMENDE))
                GROUP BY e.vehicle.id""".formatted(ENUMS), "from", from, "to", to)) {
            add(expenses, (Long) r[0], Domain.DEPENSES_DIVERSES, (BigDecimal) r[1]);
        }

        // --- Missions par camion et par type ---------------------------------------------
        Map<Long, List<MissionTypeLine>> typesByVehicle = new HashMap<>();
        Map<Long, BigDecimal> marginByVehicle = new HashMap<>();
        for (Object[] r : rows("""
                SELECT m.vehicle.id, m.serviceType.label, COUNT(m), COALESCE(SUM(m.distanceKm),0),
                       COALESCE(SUM(m.revenueAmount),0), COALESCE(SUM(m.totalCost),0),
                       COALESCE(SUM(m.marginAmount),0)
                FROM Mission m
                WHERE m.status = %sMissionStatus.TERMINEE
                  AND m.actualEnd >= :from AND m.actualEnd < :to
                GROUP BY m.vehicle.id, m.serviceType.label
                ORDER BY m.serviceType.label""".formatted(ENUMS), "from", start, "to", end)) {
            Long id = (Long) r[0];
            typesByVehicle.computeIfAbsent(id, k -> new ArrayList<>()).add(new MissionTypeLine(
                    (String) r[1], (Long) r[2], (BigDecimal) r[3], (BigDecimal) r[4], (BigDecimal) r[5]));
            marginByVehicle.merge(id, (BigDecimal) r[6], BigDecimal::add);
        }

        // --- Assemblage ---------------------------------------------------------------------
        List<VehicleSection> sections = new ArrayList<>();
        Map<Domain, BigDecimal> fleetExpenses = new EnumMap<>(Domain.class);
        Map<String, MissionTypeLine> fleetTypes = new java.util.TreeMap<>();
        for (Domain d : Domain.values()) {
            fleetExpenses.put(d, BigDecimal.ZERO);
        }
        long fleetMissions = 0;
        BigDecimal fleetRevenue = BigDecimal.ZERO;
        BigDecimal fleetKm = BigDecimal.ZERO;
        BigDecimal fleetTotal = BigDecimal.ZERO;

        for (Vehicle v : vehicles) {
            Map<Domain, BigDecimal> byDomain = new EnumMap<>(Domain.class);
            BigDecimal total = BigDecimal.ZERO;
            for (Domain d : Domain.values()) {
                BigDecimal amount = expenses.getOrDefault(v.getId(), Map.of()).getOrDefault(d, BigDecimal.ZERO);
                byDomain.put(d, amount);
                total = total.add(amount);
                fleetExpenses.merge(d, amount, BigDecimal::add);
            }

            List<MissionTypeLine> types = typesByVehicle.getOrDefault(v.getId(), List.of());
            long count = types.stream().mapToLong(MissionTypeLine::missionCount).sum();
            BigDecimal km = types.stream().map(MissionTypeLine::distanceKm).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal revenue = types.stream().map(MissionTypeLine::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (MissionTypeLine line : types) {
                fleetTypes.merge(line.typeLabel(), line, (a, b) -> new MissionTypeLine(a.typeLabel(),
                        a.missionCount() + b.missionCount(), a.distanceKm().add(b.distanceKm()),
                        a.revenue().add(b.revenue()), a.cost().add(b.cost())));
            }

            fleetMissions += count;
            fleetRevenue = fleetRevenue.add(revenue);
            fleetKm = fleetKm.add(km);
            fleetTotal = fleetTotal.add(total);

            sections.add(new VehicleSection(
                    v.getId(), v.getRegistrationNumber(),
                    ((v.getBrand() == null ? "" : v.getBrand()) + " " + (v.getModel() == null ? "" : v.getModel())).trim(),
                    v.getCity() == null ? null : v.getCity().getName(),
                    byDomain, total, types, count, km, revenue,
                    marginByVehicle.getOrDefault(v.getId(), BigDecimal.ZERO),
                    km.signum() == 0 ? null : total.divide(km, 2, RoundingMode.HALF_UP)));
        }

        sections.sort(Comparator.comparing(VehicleSection::totalExpenses).reversed());

        return new VehicleExpenseReport(from, to, Instant.now(), SecurityUtils.currentUserEmail(),
                fleetExpenses, fleetTotal, new ArrayList<>(fleetTypes.values()),
                fleetMissions, fleetRevenue, fleetKm, sections);
    }

    private List<Object[]> rows(String jpql, Object... params) {
        var query = em.createQuery(jpql, Object[].class);
        for (int i = 0; i < params.length; i += 2) {
            query.setParameter((String) params[i], params[i + 1]);
        }
        return query.getResultList();
    }

    private static void add(Map<Long, Map<Domain, BigDecimal>> map, Long vehicleId, Domain domain, BigDecimal amount) {
        if (amount == null || vehicleId == null) {
            return;
        }
        map.computeIfAbsent(vehicleId, k -> new EnumMap<>(Domain.class)).merge(domain, amount, BigDecimal::add);
    }
}
