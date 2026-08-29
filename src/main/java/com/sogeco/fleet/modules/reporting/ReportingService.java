package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.CostCategory;
import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.fuel.FuelLogRepository;
import com.sogeco.fleet.modules.insurance.InsurancePolicyRepository;
import com.sogeco.fleet.modules.insurance.TechnicalInspectionRepository;
import com.sogeco.fleet.modules.maintenance.MaintenanceLogRepository;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.reporting.dto.*;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agregations de rentabilite.
 *
 * Regle centrale : les couts et marges de mission viennent des
 * colonnes calculees par PostgreSQL sur la table missions
 * (total_cost, margin_amount). Ce service ne recalcule jamais ces
 * valeurs, il les agrege — un rapport ne peut donc jamais diverger
 * de ce qu'affiche la fiche mission correspondante.
 */
@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    /** Villes ou l'entreprise a une implantation active (memes 3 villes que les formulaires camion/mission). */
    private static final List<String> ACTIVE_CITY_NAMES = List.of("Douala", "Yaoundé", "Bafoussam");

    private final MissionRepository missionRepository;
    private final MaintenanceLogRepository maintenanceRepository;
    private final FuelLogRepository fuelLogRepository;
    private final VehicleRepository vehicleRepository;
    private final InsurancePolicyRepository insurancePolicyRepository;
    private final TechnicalInspectionRepository technicalInspectionRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<VehicleProfitability> vehicleProfitability(LocalDate from, LocalDate to) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));

        List<VehicleProfitability> results = new ArrayList<>();

        for (Object[] row : missionRepository.aggregateProfitabilityByVehicle(start, end)) {
            Long vehicleId = (Long) row[0];
            String registration = (String) row[1];
            long missionCount = (Long) row[2];
            BigDecimal revenue = (BigDecimal) row[3];
            BigDecimal directCost = (BigDecimal) row[4];
            BigDecimal directMargin = (BigDecimal) row[5];
            BigDecimal km = (BigDecimal) row[6];

            // La maintenance est un cout periodique du camion, jamais
            // rattache a une mission : elle vient d'une source separee.
            BigDecimal maintenanceCost = maintenanceRepository.totalCostForVehicle(vehicleId, from, to);
            BigDecimal netMargin = directMargin.subtract(maintenanceCost);
            BigDecimal totalCost = directCost.add(maintenanceCost);

            results.add(new VehicleProfitability(
                    vehicleId, registration, missionCount, revenue, directCost, directMargin,
                    maintenanceCost, netMargin,
                    percent(netMargin, revenue),
                    km,
                    km.signum() == 0 ? null : totalCost.divide(km, 2, RoundingMode.HALF_UP)));
        }

        results.sort((a, b) -> b.netMargin().compareTo(a.netMargin()));
        return results;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<ClientProfitability> clientProfitability(LocalDate from, LocalDate to) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));
        List<ClientProfitability> results = new ArrayList<>();

        for (Object[] row : missionRepository.aggregateProfitabilityByClient(start, end)) {
            BigDecimal revenue = (BigDecimal) row[3];
            BigDecimal margin = (BigDecimal) row[5];
            results.add(new ClientProfitability(
                    (Long) row[0], (String) row[1], (Long) row[2],
                    revenue, (BigDecimal) row[4], margin, percent(margin, revenue)));
        }
        return results;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<CorridorProfitability> corridorProfitability(LocalDate from, LocalDate to) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));
        List<CorridorProfitability> results = new ArrayList<>();

        for (Object[] row : missionRepository.aggregateProfitabilityByRoute(start, end)) {
            BigDecimal cost = (BigDecimal) row[4];
            BigDecimal km = (BigDecimal) row[6];
            results.add(new CorridorProfitability(
                    (Long) row[0], (String) row[1], (Long) row[2],
                    (BigDecimal) row[3], cost, (BigDecimal) row[5], km,
                    km.signum() == 0 ? null : cost.divide(km, 2, RoundingMode.HALF_UP)));
        }
        return results;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<AgencyProfitability> agencyProfitability(LocalDate from, LocalDate to) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));
        List<AgencyProfitability> results = new ArrayList<>();

        for (Object[] row : missionRepository.aggregateProfitabilityByAgency(start, end)) {
            results.add(new AgencyProfitability(
                    (Long) row[0], (String) row[1], (Long) row[2],
                    (BigDecimal) row[3], (BigDecimal) row[4], (BigDecimal) row[5]));
        }
        return results;
    }

    /**
     * Charges de chaque camion, mois par mois et cumulees sur l'annee — missions
     * terminees + entretien + carburant hors mission (mission.fuelCost couvre deja
     * le carburant lie a une mission, RG-6.9 : le reprendre ici ferait doublon).
     * Pas de chiffre d'affaires ni de marge, seulement ce que la flotte coute.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<VehicleExpenseSummary> vehicleExpenses(int year) {
        Instant start = toInstant(LocalDate.of(year, 1, 1));
        Instant end = toInstant(LocalDate.of(year + 1, 1, 1));
        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate = LocalDate.of(year, 12, 31);

        Map<Long, BigDecimal[]> byVehicle = new LinkedHashMap<>();

        for (Object[] row : missionRepository.findCostRowsBetween(start, end)) {
            addToMonth(byVehicle, (Long) row[0], ((Instant) row[1]).atZone(ZONE).getMonthValue(), (BigDecimal) row[2]);
        }
        for (Object[] row : maintenanceRepository.findCostRowsBetween(startDate, endDate)) {
            addToMonth(byVehicle, (Long) row[0], ((LocalDate) row[1]).getMonthValue(), (BigDecimal) row[2]);
        }
        for (Object[] row : fuelLogRepository.findStandaloneCostRowsBetween(start, end)) {
            addToMonth(byVehicle, (Long) row[0], ((Instant) row[1]).atZone(ZONE).getMonthValue(), (BigDecimal) row[2]);
        }

        List<VehicleExpenseSummary> results = new ArrayList<>();
        for (Vehicle vehicle : vehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc()) {
            BigDecimal[] months = byVehicle.get(vehicle.getId());
            List<MonthlyAmount> monthly = new ArrayList<>();
            BigDecimal yearTotal = BigDecimal.ZERO;
            for (int m = 1; m <= 12; m++) {
                BigDecimal amount = months == null || months[m - 1] == null ? BigDecimal.ZERO : months[m - 1];
                monthly.add(new MonthlyAmount(m, amount));
                yearTotal = yearTotal.add(amount);
            }

            City city = vehicle.getCity();
            results.add(new VehicleExpenseSummary(
                    vehicle.getId(), vehicle.getRegistrationNumber(),
                    city == null ? null : city.getId(), city == null ? null : city.getName(),
                    monthly, yearTotal));
        }

        results.sort((a, b) -> b.yearTotal().compareTo(a.yearTotal()));
        return results;
    }

    /** Meme decomposition que vehicleExpenses, cumulee par ville d'affectation des camions. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<CityExpenseSummary> cityExpenses(int year) {
        record CityAccumulator(Long cityId, String cityName, BigDecimal[] months, int[] vehicleCount) {
        }

        Map<Long, CityAccumulator> byCity = new LinkedHashMap<>();
        for (VehicleExpenseSummary vehicle : vehicleExpenses(year)) {
            if (vehicle.cityId() == null) {
                continue; // camion sans site affecte : ne peut pas etre rattache a une ville
            }
            CityAccumulator acc = byCity.computeIfAbsent(vehicle.cityId(),
                    id -> new CityAccumulator(id, vehicle.cityName(), new BigDecimal[12], new int[]{0}));
            acc.vehicleCount()[0]++;
            for (MonthlyAmount ma : vehicle.monthly()) {
                int idx = ma.month() - 1;
                acc.months()[idx] = (acc.months()[idx] == null ? BigDecimal.ZERO : acc.months()[idx]).add(ma.amount());
            }
        }

        List<CityExpenseSummary> results = new ArrayList<>();
        for (CityAccumulator acc : byCity.values()) {
            List<MonthlyAmount> monthly = new ArrayList<>();
            BigDecimal yearTotal = BigDecimal.ZERO;
            for (int m = 1; m <= 12; m++) {
                BigDecimal amount = acc.months()[m - 1] == null ? BigDecimal.ZERO : acc.months()[m - 1];
                monthly.add(new MonthlyAmount(m, amount));
                yearTotal = yearTotal.add(amount);
            }
            results.add(new CityExpenseSummary(acc.cityId(), acc.cityName(), acc.vehicleCount()[0], monthly, yearTotal));
        }

        results.sort((a, b) -> b.yearTotal().compareTo(a.yearTotal()));
        return results;
    }

    private void addToMonth(Map<Long, BigDecimal[]> byVehicle, Long vehicleId, int month, BigDecimal cost) {
        if (cost == null) {
            return;
        }
        BigDecimal[] months = byVehicle.computeIfAbsent(vehicleId, id -> new BigDecimal[12]);
        int idx = month - 1;
        months[idx] = (months[idx] == null ? BigDecimal.ZERO : months[idx]).add(cost);
    }

    /**
     * Repartition du cout total par categorie. Exclut volontairement la
     * quote-part salariale des chauffeurs (ce n'est pas une charge de
     * gestion de flotte au sens de cet ecran) et inclut a la place les
     * primes d'assurance et les frais de visite technique — voir
     * CostCategory. La somme des categories ne reconcilie donc plus
     * avec le "Cout total" de fleetKpis.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public CostBreakdownResponse costBreakdown(LocalDate from, LocalDate to) {
        return computeCostBreakdown(from, to);
    }

    private CostBreakdownResponse computeCostBreakdown(LocalDate from, LocalDate to) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));

        List<Object[]> rows = missionRepository.aggregateCostComponents(start, end);
        Object[] row = rows.isEmpty()
                ? new Object[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}
                : rows.get(0);

        BigDecimal fuel = (BigDecimal) row[0];
        BigDecimal toll = (BigDecimal) row[1];
        BigDecimal other = (BigDecimal) row[3];
        BigDecimal missionFee = (BigDecimal) row[4];
        BigDecimal insurance = insurancePolicyRepository.totalPremiumCost(from, to);
        BigDecimal inspection = technicalInspectionRepository.totalCost(from, to);

        // LAVERIE isolee du reste de la maintenance : meme requete groupee par
        // categorie que l'anneau de l'ecran Maintenance, pas un second total.
        BigDecimal maintenance = BigDecimal.ZERO;
        BigDecimal laverie = BigDecimal.ZERO;
        for (Object[] maintenanceRow : maintenanceRepository.aggregateByCategory(from, to)) {
            BigDecimal cost = (BigDecimal) maintenanceRow[2];
            if (maintenanceRow[0] == MaintenanceCategory.LAVERIE) {
                laverie = laverie.add(cost);
            } else {
                maintenance = maintenance.add(cost);
            }
        }

        BigDecimal total = fuel.add(toll).add(other).add(missionFee)
                .add(maintenance).add(laverie).add(insurance).add(inspection);

        List<CostBreakdownResponse.CategoryAmount> categories = List.of(
                categoryAmount(CostCategory.CARBURANT, fuel, total),
                categoryAmount(CostCategory.MAINTENANCE, maintenance, total),
                categoryAmount(CostCategory.LAVERIE, laverie, total),
                categoryAmount(CostCategory.ASSURANCE, insurance, total),
                categoryAmount(CostCategory.VISITE_TECHNIQUE, inspection, total),
                categoryAmount(CostCategory.PEAGES, toll, total),
                categoryAmount(CostCategory.FRAIS_MISSION, missionFee, total),
                categoryAmount(CostCategory.AUTRES, other, total));

        return new CostBreakdownResponse(total, categories);
    }

    private CostBreakdownResponse.CategoryAmount categoryAmount(CostCategory category, BigDecimal amount, BigDecimal total) {
        return new CostBreakdownResponse.CategoryAmount(category, amount, percent(amount, total));
    }

    /**
     * Evolution mensuelle du chiffre d'affaires, du cout et de la marge.
     * Meme logique que fleetKpis, rejouee mois par mois : a l'echelle
     * d'une flotte de quelques camions, une boucle en memoire reste
     * plus simple qu'une requete SQL groupee par mois.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<MonthlyFinancialPoint> monthlyTrend(LocalDate from, LocalDate to) {
        List<MonthlyFinancialPoint> points = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);

        while (!cursor.isAfter(lastMonth)) {
            LocalDate monthEnd = cursor.plusMonths(1).minusDays(1);
            LocalDate effectiveEnd = monthEnd.isAfter(to) ? to : monthEnd;

            Instant start = toInstant(cursor);
            Instant end = toInstant(effectiveEnd.plusDays(1));

            List<Mission> completed = missionRepository.findByStatusAndActualEndBetween(
                    MissionStatus.TERMINEE, start, end);

            BigDecimal revenue = sum(completed, Mission::getRevenueAmount);
            BigDecimal directCost = sum(completed, Mission::getTotalCost);
            BigDecimal directMargin = sum(completed, Mission::getMarginAmount);
            BigDecimal maintenanceCost = maintenanceRepository.totalCost(cursor, effectiveEnd);
            CostBreakdownResponse monthBreakdown = computeCostBreakdown(cursor, effectiveEnd);

            points.add(new MonthlyFinancialPoint(
                    cursor, revenue, directCost.add(maintenanceCost), directMargin.subtract(maintenanceCost),
                    monthBreakdown.categories()));

            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    /**
     * Cout carburant et cout maintenance, mois par mois, restreints aux
     * camions des villes d'implantation actives — la courbe "Performance
     * de la flotte" de l'ecran d'accueil ne doit pas compter un camion
     * hors perimetre (par exemple affecte a une ville sans agence).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public List<FleetPerformancePoint> fleetPerformance(LocalDate from, LocalDate to) {
        List<FleetPerformancePoint> points = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate lastMonth = to.withDayOfMonth(1);

        while (!cursor.isAfter(lastMonth)) {
            LocalDate monthEnd = cursor.plusMonths(1).minusDays(1);
            LocalDate effectiveEnd = monthEnd.isAfter(to) ? to : monthEnd;

            Instant start = toInstant(cursor);
            Instant end = toInstant(effectiveEnd.plusDays(1));

            BigDecimal fuelCost = fuelLogRepository.totalCostForCities(start, end, ACTIVE_CITY_NAMES);
            BigDecimal maintenanceCost = maintenanceRepository.totalCostForCities(cursor, effectiveEnd, ACTIVE_CITY_NAMES);

            points.add(new FleetPerformancePoint(cursor, fuelCost, maintenanceCost));

            cursor = cursor.plusMonths(1);
        }
        return points;
    }

    /**
     * Indicateurs consolides. Ponctualite et taux de remplissage sont
     * calcules a partir des missions chargees en memoire — a l'echelle
     * de 11 camions, quelques centaines de missions par mois au plus,
     * c'est largement plus simple qu'une requete SQL dediee, et ca
     * reutilise Mission.isOnTime() / fillRate() sans dupliquer la regle.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DASHBOARD_EXECUTIVE_READ')")
    public FleetKpis fleetKpis(LocalDate from, LocalDate to, int punctualityMarginMinutes) {
        Instant start = toInstant(from);
        Instant end = toInstant(to.plusDays(1));

        List<Mission> completed = missionRepository.findByStatusAndActualEndBetween(
                MissionStatus.TERMINEE, start, end);

        BigDecimal revenue = sum(completed, Mission::getRevenueAmount);
        BigDecimal directCost = sum(completed, Mission::getTotalCost);
        BigDecimal directMargin = sum(completed, Mission::getMarginAmount);
        BigDecimal km = sum(completed, Mission::getDistanceKm);

        BigDecimal maintenanceCost = maintenanceRepository.totalCost(from, to);
        BigDecimal netMargin = directMargin.subtract(maintenanceCost);
        BigDecimal totalCost = directCost.add(maintenanceCost);

        long withArrival = completed.stream().filter(m -> m.getPlannedArrival() != null).count();
        long onTime = completed.stream()
                .filter(m -> Boolean.TRUE.equals(m.isOnTime(punctualityMarginMinutes))).count();

        List<BigDecimal> fillRates = completed.stream()
                .map(Mission::fillRate).filter(java.util.Objects::nonNull).toList();
        BigDecimal avgFillRate = fillRates.isEmpty() ? null
                : fillRates.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(fillRates.size()), 1, RoundingMode.HALF_UP);

        long fleetSize = vehicleRepository.countByActiveTrue();
        long periodMinutes = Duration.between(start, end).toMinutes();
        Double missionMinutes = missionRepository.totalMissionMinutes(start, end);
        BigDecimal utilization = fleetSize == 0 || periodMinutes == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf((missionMinutes == null ? 0 : missionMinutes) * 100
                        / (fleetSize * periodMinutes)).setScale(1, RoundingMode.HALF_UP);

        return new FleetKpis(
                from, to, revenue, directCost, maintenanceCost, netMargin,
                percent(netMargin, revenue),
                utilization,
                withArrival == 0 ? null : BigDecimal.valueOf(onTime * 100.0 / withArrival)
                        .setScale(1, RoundingMode.HALF_UP),
                avgFillRate,
                availabilityRate(),
                km.signum() == 0 ? null : totalCost.divide(km, 2, RoundingMode.HALF_UP),
                km);
    }

    // ------------------------------------------------------------------

    private BigDecimal availabilityRate() {
        long total = vehicleRepository.countByActiveTrue();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        long immobilized = vehicleRepository.countByStatusAndActiveTrue(
                        com.sogeco.fleet.common.enums.VehicleStatus.EN_MAINTENANCE)
                + vehicleRepository.countByStatusAndActiveTrue(
                        com.sogeco.fleet.common.enums.VehicleStatus.EN_PANNE)
                + vehicleRepository.countByStatusAndActiveTrue(
                        com.sogeco.fleet.common.enums.VehicleStatus.HORS_SERVICE);

        return BigDecimal.valueOf((total - immobilized) * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<Mission> missions, java.util.function.Function<Mission, BigDecimal> field) {
        return missions.stream().map(field).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Instant toInstant(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
