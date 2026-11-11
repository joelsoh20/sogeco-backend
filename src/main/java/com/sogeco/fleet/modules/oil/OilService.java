package com.sogeco.fleet.modules.oil;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.oil.dto.OilDistributionRequest;
import com.sogeco.fleet.modules.oil.dto.OilPurchaseRequest;
import com.sogeco.fleet.modules.oil.dto.OilSummaryResponse;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentRepository;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Suivi du fut d'huile moteur, tenu PAR VILLE : approvisionnements, huile donnee a chaque camion,
 * stock restant. Le stock d'une ville = ses approvisionnements - les sorties vers les camions de
 * cette ville. Une sortie ne peut pas depasser ce qui reste (RG-7.12). Un gestionnaire ne voit et
 * ne modifie que sa ville (RG-13.4) ; l'administrateur choisit la ville.
 *
 * Achats et sorties sont modifiables en cas d'erreur, sous la meme contrainte : le stock d'aucune
 * ville ne peut devenir negatif.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OilService {

    /** Villes concernees par le suivi d'huile (noms sans accent, en minuscules). */
    private static final java.util.Set<String> OIL_CITIES = java.util.Set.of("douala", "yaounde", "bafoussam");

    /** Vrai si la ville fait partie de celles qui tiennent un fut d'huile (Douala, Yaounde, Bafoussam). */
    public static boolean isOilCity(City city) {
        String normalized = java.text.Normalizer.normalize(city.getName(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
        return OIL_CITIES.contains(normalized);
    }

    private final OilPurchaseRepository purchaseRepository;
    private final OilDistributionRepository distributionRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final CityRepository cityRepository;

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    /** cityId : filtre demande par l'administrateur (null = toutes les villes) ; ignore pour un gestionnaire. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAINTENANCE_READ')")
    public OilSummaryResponse summary(Long cityId) {
        return buildSummary(SecurityUtils.currentCityId().orElse(cityId));
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_CREATE')")
    public OilSummaryResponse purchase(OilPurchaseRequest request) {
        City city = resolvePurchaseCity(request.cityId());
        purchaseRepository.save(OilPurchase.builder()
                .city(city)
                .purchaseDate(request.purchaseDate())
                .quantityLiters(request.quantityLiters())
                .note(blankToNull(request.note()))
                .build());
        log.info("Approvisionnement en huile a {} : {} L le {} par {}", city.getName(),
                request.quantityLiters(), request.purchaseDate(), SecurityUtils.currentUserEmail());
        return buildSummary(city.getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_UPDATE')")
    public OilSummaryResponse updatePurchase(Long id, OilPurchaseRequest request) {
        OilPurchase purchase = purchaseRepository.findWithCityById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Achat d'huile", id));
        Long scope = SecurityUtils.currentCityId().orElse(null);
        if (scope != null && !scope.equals(purchase.getCity().getId())) {
            throw new ResourceNotFoundException("Achat d'huile", id);
        }
        City oldCity = purchase.getCity();
        City newCity = scope != null || request.cityId() == null ? oldCity : findCity(request.cityId());
        BigDecimal oldQty = purchase.getQuantityLiters();

        // Le stock de la ville d'origine ne doit pas devenir negatif (achat reduit ou deplace).
        BigDecimal oldCityAfter = remaining(oldCity.getId()).subtract(oldQty);
        if (newCity.getId().equals(oldCity.getId())) {
            oldCityAfter = oldCityAfter.add(request.quantityLiters());
        }
        if (oldCityAfter.signum() < 0) {
            throw new BusinessException("RG-7.12",
                    "Modification impossible : %s L ont deja ete donnes a partir de cet achat (stock de %s)"
                            .formatted(oldCityAfter.negate().stripTrailingZeros().toPlainString(), oldCity.getName()),
                    HttpStatus.CONFLICT);
        }

        purchase.setCity(newCity);
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setQuantityLiters(request.quantityLiters());
        purchase.setNote(blankToNull(request.note()));
        log.info("Achat d'huile {} modifie par {}", id, SecurityUtils.currentUserEmail());
        return buildSummary(newCity.getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_CREATE')")
    public OilSummaryResponse distribute(OilDistributionRequest request) {
        Vehicle vehicle = scopedVehicle(request.vehicleId());
        City city = cityOf(vehicle);

        BigDecimal remaining = remaining(city.getId());
        if (request.quantityLiters().compareTo(remaining) > 0) {
            throw insufficient(remaining, city);
        }

        distributionRepository.save(OilDistribution.builder()
                .vehicle(vehicle)
                .distributionDate(request.distributionDate())
                .quantityLiters(request.quantityLiters())
                .recipientName(recipient(request.recipientName(), vehicle))
                .note(blankToNull(request.note()))
                .build());
        log.info("Huile donnee a {} : {} L le {} par {}", vehicle.getRegistrationNumber(),
                request.quantityLiters(), request.distributionDate(), SecurityUtils.currentUserEmail());
        return buildSummary(city.getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_UPDATE')")
    public OilSummaryResponse updateDistribution(Long id, OilDistributionRequest request) {
        OilDistribution distribution = distributionRepository.findWithVehicleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sortie d'huile", id));
        Long scope = SecurityUtils.currentCityId().orElse(null);
        City oldCity = distribution.getVehicle().getCity();
        if (scope != null && (oldCity == null || !scope.equals(oldCity.getId()))) {
            throw new ResourceNotFoundException("Sortie d'huile", id);
        }

        Vehicle vehicle = scopedVehicle(request.vehicleId());
        City newCity = cityOf(vehicle);

        // Si la sortie reste dans la meme ville, son ancienne quantite est rendue au stock avant controle.
        BigDecimal available = remaining(newCity.getId());
        if (oldCity != null && oldCity.getId().equals(newCity.getId())) {
            available = available.add(distribution.getQuantityLiters());
        }
        if (request.quantityLiters().compareTo(available) > 0) {
            throw insufficient(available, newCity);
        }

        boolean sameVehicle = vehicle.getId().equals(distribution.getVehicle().getId());
        distribution.setVehicle(vehicle);
        distribution.setDistributionDate(request.distributionDate());
        distribution.setQuantityLiters(request.quantityLiters());
        distribution.setRecipientName(sameVehicle && request.recipientName() != null && !request.recipientName().isBlank()
                ? request.recipientName().trim() : recipient(request.recipientName(), vehicle));
        distribution.setNote(blankToNull(request.note()));
        log.info("Sortie d'huile {} modifiee par {}", id, SecurityUtils.currentUserEmail());
        return buildSummary(newCity.getId());
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private BigDecimal remaining(Long cityId) {
        return purchaseRepository.total(cityId).subtract(distributionRepository.total(cityId));
    }

    private City resolvePurchaseCity(Long requestedCityId) {
        Long scope = SecurityUtils.currentCityId().orElse(null);
        Long cityId = scope != null ? scope : requestedCityId;
        if (cityId == null) {
            throw new BusinessException("RG-7.13", "Choisissez la ville du stock d'huile", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        return findCity(cityId);
    }

    /** Ville du fut d'huile : l'huile n'est suivie qu'a Douala, Yaounde et Bafoussam. */
    private City findCity(Long id) {
        City city = cityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ville", id));
        if (!isOilCity(city)) {
            throw new BusinessException("RG-7.13",
                    "Le suivi d'huile ne concerne que Douala, Yaounde et Bafoussam", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        return city;
    }

    /** Camion existant et, pour un gestionnaire, de sa ville (RG-13.4). */
    private Vehicle scopedVehicle(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion", vehicleId));
        Long scope = SecurityUtils.currentCityId().orElse(null);
        if (scope != null && (vehicle.getCity() == null || !scope.equals(vehicle.getCity().getId()))) {
            throw new ResourceNotFoundException("Camion", vehicleId);
        }
        return vehicle;
    }

    private static City cityOf(Vehicle vehicle) {
        if (vehicle.getCity() == null) {
            throw new BusinessException("RG-7.13",
                    "Ce camion n'a pas de ville : impossible de savoir quel stock d'huile utiliser",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (!isOilCity(vehicle.getCity())) {
            throw new BusinessException("RG-7.13",
                    "Le suivi d'huile ne concerne que Douala, Yaounde et Bafoussam", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        return vehicle.getCity();
    }

    private static BusinessException insufficient(BigDecimal remaining, City city) {
        return new BusinessException("RG-7.12",
                "Stock d'huile insuffisant a %s : il reste %s L".formatted(city.getName(),
                        remaining.stripTrailingZeros().toPlainString()),
                HttpStatus.CONFLICT);
    }

    private String recipient(String requested, Vehicle vehicle) {
        String name = blankToNull(requested);
        if (name != null) {
            return name;
        }
        return assignmentRepository.findByVehicleIdAndEndDateIsNull(vehicle.getId())
                .map(a -> a.getDriver().getFullName()).orElse(null);
    }

    private OilSummaryResponse buildSummary(Long cityId) {
        BigDecimal purchased = purchaseRepository.total(cityId);
        BigDecimal distributed = distributionRepository.total(cityId);

        Map<Long, BigDecimal> distributedByCity = new HashMap<>();
        for (Object[] row : distributionRepository.totalsByCity()) {
            distributedByCity.put(((Number) row[0]).longValue(), (BigDecimal) row[1]);
        }
        Long scope = SecurityUtils.currentCityId().orElse(null);
        List<OilSummaryResponse.CityStock> byCity = new ArrayList<>();
        for (Object[] row : purchaseRepository.totalsByCity()) {
            Long id = ((Number) row[0]).longValue();
            if (scope != null && !scope.equals(id)) {
                continue;
            }
            BigDecimal bought = (BigDecimal) row[2];
            BigDecimal given = distributedByCity.getOrDefault(id, BigDecimal.ZERO);
            byCity.add(new OilSummaryResponse.CityStock(id, (String) row[1], bought, given, bought.subtract(given)));
        }
        byCity.sort(java.util.Comparator.comparing(OilSummaryResponse.CityStock::cityName));

        return new OilSummaryResponse(cityId, purchased, distributed, purchased.subtract(distributed), byCity,
                purchaseRepository.recent(cityId, PageRequest.of(0, 10)).stream()
                        .map(p -> new OilSummaryResponse.Purchase(p.getId(), p.getPurchaseDate(),
                                p.getCity().getId(), p.getCity().getName(), p.getQuantityLiters(), p.getNote())).toList(),
                distributionRepository.recent(cityId, PageRequest.of(0, 20)).stream()
                        .map(d -> new OilSummaryResponse.Distribution(d.getId(), d.getDistributionDate(),
                                d.getVehicle().getId(), d.getVehicle().getRegistrationNumber(),
                                d.getVehicle().getCity() == null ? null : d.getVehicle().getCity().getName(),
                                d.getRecipientName(), d.getQuantityLiters(), d.getNote())).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
