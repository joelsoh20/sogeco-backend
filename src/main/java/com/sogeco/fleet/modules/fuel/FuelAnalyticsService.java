package com.sogeco.fleet.modules.fuel;

import com.sogeco.fleet.common.enums.FuelLogStatus;
import com.sogeco.fleet.modules.driver.dto.DriverFuelEconomyResponse;
import com.sogeco.fleet.modules.fuel.dto.FuelStatsResponse;
import com.sogeco.fleet.modules.fuel.dto.TankLevelResponse;
import com.sogeco.fleet.modules.fuel.dto.TankLevelResponse.TankLevelSource;
import com.sogeco.fleet.modules.fuel.dto.WeeklyRefuelResponse;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.GpsDailyStatRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregats de l'ecran Carburant : compteurs de tete et repartitions
 * par camion, par jour et par station.
 */
@Service
@RequiredArgsConstructor
public class FuelAnalyticsService {

    private final FuelLogRepository repository;
    private final MissionRepository missionRepository;
    private final VehicleRepository vehicleRepository;
    private final GpsDailyStatRepository dailyStatRepository;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public FuelStatsResponse stats(LocalDate from, LocalDate to, Long cityId) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        BigDecimal totalCost = repository.totalCostForCity(start, end, cityId);
        BigDecimal totalLiters = repository.totalLitersForCity(start, end, cityId);
        BigDecimal distance = missionRepository.totalDistanceForCity(start, end, cityId);

        // Consommation moyenne du parc : litres x 100 / km parcourus.
        BigDecimal averageConsumption = distance != null && distance.signum() > 0
                ? totalLiters.multiply(BigDecimal.valueOf(100))
                    .divide(distance, 2, RoundingMode.HALF_UP)
                : null;

        BigDecimal costPerKm = distance != null && distance.signum() > 0
                ? totalCost.divide(distance, 2, RoundingMode.HALF_UP)
                : null;

        List<FuelStatsResponse.VehicleFuelBreakdown> breakdown = new ArrayList<>();
        for (Object[] row : repository.aggregateByVehicleForCity(start, end, cityId)) {
            BigDecimal vehicleCost = (BigDecimal) row[3];
            BigDecimal share = totalCost.signum() == 0
                    ? BigDecimal.ZERO
                    : vehicleCost.multiply(BigDecimal.valueOf(100))
                        .divide(totalCost, 1, RoundingMode.HALF_UP);

            breakdown.add(new FuelStatsResponse.VehicleFuelBreakdown(
                    (Long) row[0],
                    (String) row[1],
                    (BigDecimal) row[2],
                    vehicleCost,
                    row[4] == null ? null : ((Number) row[4]).doubleValue() == 0
                            ? null : BigDecimal.valueOf(((Number) row[4]).doubleValue())
                                .setScale(2, RoundingMode.HALF_UP),
                    share));
        }

        // Tendance sur 6 mois : independante de from/to (toujours les 6 derniers mois
        // pleins), pour donner du recul au-dela de la seule periode selectionnee.
        LocalDate sixMonthsAgo = LocalDate.now(zone).withDayOfMonth(1).minusMonths(5);
        Instant trendStart = sixMonthsAgo.atStartOfDay(zone).toInstant();
        Instant trendEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();

        List<FuelStatsResponse.MonthlyConsumption> trend = new ArrayList<>();
        for (Object[] row : repository.aggregateByMonthForCity(trendStart, trendEnd, cityId)) {
            trend.add(new FuelStatsResponse.MonthlyConsumption(toLocalDate(row[0]), (BigDecimal) row[1]));
        }

        return new FuelStatsResponse(
                totalCost, totalLiters, averageConsumption, costPerKm, distance,
                repository.countByStatus(FuelLogStatus.ANOMALIE),
                breakdown, trend);
    }

    /** date_trunc renvoie un Instant (timestamptz) : on ne garde que le jour, dans le fuseau de l'entreprise. */
    private LocalDate toLocalDate(Object value) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        return ((Instant) value).atZone(zone).toLocalDate();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public List<Object[]> byStation(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        return repository.aggregateByStation(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Classement des chauffeurs les plus economes, pour l'ecran
     * Chauffeurs et Performance. Independant de FUEL_READ : c'est
     * DRIVER_READ qui protege cet appel, verifie par l'appelant
     * (DriverController) — la donnee sert une fiche chauffeur, pas
     * l'ecran carburant lui-meme.
     */
    @Transactional(readOnly = true)
    public List<DriverFuelEconomyResponse> topEconomyDrivers(LocalDate from, LocalDate to, int limit) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<DriverFuelEconomyResponse> results = new ArrayList<>();
        for (Object[] row : repository.aggregateByDriver(start, end)) {
            if (results.size() >= limit) break;
            results.add(new DriverFuelEconomyResponse(
                    (Long) row[0], (String) row[1],
                    BigDecimal.valueOf(((Number) row[2]).doubleValue()).setScale(1, RoundingMode.HALF_UP),
                    (BigDecimal) row[3], (BigDecimal) row[4]));
        }
        return results;
    }

    /**
     * Niveau de carburant estime de chaque camion actif, pour l'ecran
     * Carburant. La telematique (mesure reelle) est prioritaire quand
     * elle est disponible ; sinon, on estime a partir du dernier plein
     * complet et de la distance parcourue depuis (methode "plein a
     * plein" : le reservoir est suppose rempli a sa capacite au moment
     * d'un plein complet).
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public List<TankLevelResponse> tankLevels(Long cityId) {
        return vehicleRepository.findActiveForCity(cityId).stream()
                .map(this::tankLevelFor)
                .toList();
    }

    /** Expose aussi a TrackingService : la carte affiche le meme niveau (mesure ou estime) que l'ecran Carburant. */
    public TankLevelResponse tankLevelFor(Vehicle vehicle) {
        if (vehicle.getFuelLevelPercent() != null) {
            return new TankLevelResponse(
                    vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getTankCapacityLiters(),
                    vehicle.getFuelLevelLiters(), vehicle.getFuelLevelPercent(),
                    null, null, TankLevelSource.TELEMATIQUE);
        }

        BigDecimal tankCapacity = vehicle.getTankCapacityLiters();
        BigDecimal avgConsumption = vehicle.getAvgFuelConsumption();
        // L'entreprise ne fait jamais de plein complet : findPreviousFullTank ne
        // trouverait donc jamais rien. Le tout premier plein connu sert de repere
        // a la place, en supposant le reservoir vide avant lui — sous-estime
        // probablement le niveau reel au tout debut de l'historique, mais ne
        // fabrique aucune donnee non observee, et se corrige au fil des pleins
        // suivants (fuelAddedSince).
        var firstLog = repository.findFirstLog(vehicle.getId());

        if (tankCapacity == null || avgConsumption == null || firstLog.isEmpty()) {
            return new TankLevelResponse(
                    vehicle.getId(), vehicle.getRegistrationNumber(), tankCapacity,
                    null, null, null, null, TankLevelSource.INDISPONIBLE);
        }

        FuelLog anchor = firstLog.get();
        BigDecimal odometerAtAnchor = anchor.getOdometerAfter();
        BigDecimal distanceSince = vehicle.getCurrentKilometers().subtract(odometerAtAnchor).max(BigDecimal.ZERO);
        BigDecimal fuelAddedSince = repository.sumQuantitySince(vehicle.getId(), anchor.getFuelDatetime());

        BigDecimal consumed = distanceSince.multiply(avgConsumption)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal estimatedLiters = anchor.getQuantityLiters().add(fuelAddedSince).subtract(consumed)
                .max(BigDecimal.ZERO).min(tankCapacity)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal estimatedPercent = tankCapacity.signum() == 0
                ? null
                : estimatedLiters.multiply(BigDecimal.valueOf(100))
                    .divide(tankCapacity, 2, RoundingMode.HALF_UP);

        return new TankLevelResponse(
                vehicle.getId(), vehicle.getRegistrationNumber(), tankCapacity,
                estimatedLiters, estimatedPercent, distanceSince,
                anchor.getFuelDatetime(), TankLevelSource.ESTIMATION_DISTANCE);
    }

    /**
     * Kilometrage, consommation et carburant a ajouter sur la periode —
     * pense pour les vehicules a suivi allege (moto, tricycle, voiture de
     * livraison) : pas de mission a rapprocher, juste de quoi savoir
     * combien mettre dans le reservoir au plein du samedi.
     *
     * Le niveau avant plein reutilise tankLevelFor() (meme estimation que
     * l'ecran Carburant) : la seule donnee propre a cet ecran est la
     * distance parcourue sur la periode, agregee depuis gps_daily_stats.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FUEL_READ')")
    public List<WeeklyRefuelResponse> weeklyRefuel(LocalDate from, LocalDate to, Long cityId) {
        return vehicleRepository.findActiveForCity(cityId).stream()
                .map(vehicle -> {
                    BigDecimal distance = dailyStatRepository.totalDistance(vehicle.getId(), from, to);
                    TankLevelResponse tank = tankLevelFor(vehicle);

                    BigDecimal suggestedRefill = tank.tankCapacityLiters() != null
                            && tank.estimatedFuelLiters() != null
                            ? tank.tankCapacityLiters().subtract(tank.estimatedFuelLiters())
                                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                            : null;

                    return new WeeklyRefuelResponse(
                            vehicle.getId(), vehicle.getRegistrationNumber(), vehicle.getBodyType(),
                            distance, vehicle.getAvgFuelConsumption(),
                            tank.tankCapacityLiters(), tank.estimatedFuelLiters(),
                            suggestedRefill, tank.source());
                })
                .toList();
    }
}
