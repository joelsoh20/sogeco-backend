package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.oil.OilDistribution;
import com.sogeco.fleet.modules.oil.OilDistributionRepository;
import com.sogeco.fleet.modules.oil.OilPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Donnees du suivi du fut d'huile sur une periode, ville par ville : stock en debut de periode,
 * approvisionnements, huile donnee a chaque camion, totaux par camion. Le stock restant en fin de
 * periode d'une ville = stock initial + approvisionnements - sorties. Un gestionnaire n'obtient que
 * sa ville ; l'administrateur (ou le comptable, sans ville) peut cibler une ville ou les avoir toutes.
 */
@Service
@RequiredArgsConstructor
public class OilReportService {

    private final OilPurchaseRepository purchaseRepository;
    private final OilDistributionRepository distributionRepository;
    private final CityRepository cityRepository;

    public record Purchase(LocalDate date, BigDecimal quantity, String note) {
    }

    public record Output(LocalDate date, String registrationNumber, boolean external, String recipient, BigDecimal quantity) {
    }

    public record VehicleTotal(String registrationNumber, boolean external, String cityName, String recipients,
                               int outputCount, BigDecimal quantity) {
    }

    /** Le fut d'une ville sur la periode. */
    public record CityOil(Long cityId, String cityName, BigDecimal opening, List<Purchase> purchases,
                          List<Output> outputs) {

        public BigDecimal purchased() {
            return purchases.stream().map(Purchase::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal consumed() {
            return outputs.stream().map(Output::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal remaining() {
            return opening.add(purchased()).subtract(consumed());
        }
    }

    public record OilReport(LocalDate from, LocalDate to, List<CityOil> cities, List<VehicleTotal> byVehicle) {
    }

    /** Immatriculation : celle du camion de la flotte, sinon celle saisie pour un vehicule hors flotte. */
    private static String plate(OilDistribution d) {
        return d.getVehicle() != null ? d.getVehicle().getRegistrationNumber() : d.getExternalPlate();
    }

    /** cityId : ville demandee (ignoree pour un gestionnaire, limite a la sienne) ; null = toutes les villes. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public OilReport build(LocalDate from, LocalDate to, Long cityId) {
        if (to.isBefore(from)) {
            throw new BusinessException("REPORT_PERIOD",
                    "La date de fin precede la date de debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Long scope = SecurityUtils.currentCityId().orElse(cityId);

        List<City> candidates = scope != null
                ? cityRepository.findById(scope).map(List::of).orElse(List.of())
                : cityRepository.findAll().stream().filter(com.sogeco.fleet.modules.oil.OilService::isOilCity)
                        .sorted(Comparator.comparing(City::getName)).toList();

        List<CityOil> cities = new ArrayList<>();
        List<VehicleTotal> byVehicle = new ArrayList<>();
        for (City city : candidates) {
            BigDecimal opening = purchaseRepository.totalBefore(from, city.getId())
                    .subtract(distributionRepository.totalBefore(from, city.getId()));
            List<Purchase> purchases = purchaseRepository.inPeriod(from, to, city.getId()).stream()
                    .map(p -> new Purchase(p.getPurchaseDate(), p.getQuantityLiters(), p.getNote())).toList();
            List<OilDistribution> distributions = distributionRepository.inPeriod(from, to, city.getId());

            // Sans activite ni stock sur la periode, une ville n'apparait pas (sauf si elle est demandee).
            if (scope == null && opening.signum() == 0 && purchases.isEmpty() && distributions.isEmpty()) {
                continue;
            }
            cities.add(new CityOil(city.getId(), city.getName(), opening, purchases,
                    distributions.stream().map(d -> new Output(d.getDistributionDate(), plate(d), d.getVehicle() == null,
                            d.getRecipientName(), d.getQuantityLiters())).toList()));

            Map<String, List<OilDistribution>> byPlate = new LinkedHashMap<>();
            for (OilDistribution d : distributions) {
                byPlate.computeIfAbsent(plate(d), k -> new ArrayList<>()).add(d);
            }
            byPlate.forEach((plate, list) -> byVehicle.add(new VehicleTotal(plate, list.get(0).getVehicle() == null, city.getName(),
                    list.stream().map(OilDistribution::getRecipientName).filter(n -> n != null && !n.isBlank())
                            .distinct().collect(Collectors.joining(", ")),
                    list.size(),
                    list.stream().map(OilDistribution::getQuantityLiters).reduce(BigDecimal.ZERO, BigDecimal::add))));
        }
        byVehicle.sort(Comparator.comparing(VehicleTotal::quantity).reversed());
        return new OilReport(from, to, cities, byVehicle);
    }
}
