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
import java.util.Locale;
import java.util.Map;

/**
 * Suivi du fut d'huile moteur, tenu PAR VILLE : approvisionnements, huile donnee, stock restant.
 * Le stock d'une ville = ses approvisionnements - les sorties prises sur son fut. Une sortie ne peut pas
 * depasser ce qui reste (RG-7.12). Un gestionnaire ne voit et ne modifie que sa ville (RG-13.4) ;
 * l'administrateur choisit la ville.
 *
 * L'huile peut etre donnee a un camion de la flotte (stock de la ville du camion) ou a un vehicule HORS FLOTTE
 * (ou d'une autre ville), saisi par son immatriculation : la sortie est alors prise sur le fut de la ville de
 * celui qui la donne (sa ville pour un gestionnaire, la ville choisie pour l'administrateur).
 *
 * Achats et sorties sont modifiables en cas d'erreur, sous la meme contrainte : le stock d'aucune ville ne
 * peut devenir negatif.
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
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
        return OIL_CITIES.contains(normalized);
    }

    private final OilPurchaseRepository purchaseRepository;
    private final OilDistributionRepository distributionRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAssignmentRepository assignmentRepository;
    private final CityRepository cityRepository;

    /** Destinataire de l'huile : un camion de la flotte, ou une immatriculation hors flotte, et le stock sollicite. */
    private record Target(Vehicle vehicle, String externalPlate, City city) {
    }

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
    // Achats
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_CREATE')")
    public OilSummaryResponse purchase(OilPurchaseRequest request) {
        City city = resolveStockCity(request.cityId());
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

    // ------------------------------------------------------------------
    // Sorties
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_CREATE')")
    public OilSummaryResponse distribute(OilDistributionRequest request) {
        Target target = resolveTarget(request);

        BigDecimal remaining = remaining(target.city().getId());
        if (request.quantityLiters().compareTo(remaining) > 0) {
            throw insufficient(remaining, target.city());
        }

        distributionRepository.save(OilDistribution.builder()
                .vehicle(target.vehicle())
                .externalPlate(target.externalPlate())
                .city(target.city())
                .distributionDate(request.distributionDate())
                .quantityLiters(request.quantityLiters())
                .recipientName(recipient(request.recipientName(), target.vehicle()))
                .note(blankToNull(request.note()))
                .build());
        log.info("Huile donnee a {} : {} L le {} (stock {}) par {}", label(target),
                request.quantityLiters(), request.distributionDate(), target.city().getName(),
                SecurityUtils.currentUserEmail());
        return buildSummary(target.city().getId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('MAINTENANCE_UPDATE')")
    public OilSummaryResponse updateDistribution(Long id, OilDistributionRequest request) {
        OilDistribution distribution = distributionRepository.findWithVehicleById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sortie d'huile", id));
        Long scope = SecurityUtils.currentCityId().orElse(null);
        City oldCity = distribution.getCity();
        if (scope != null && !scope.equals(oldCity.getId())) {
            throw new ResourceNotFoundException("Sortie d'huile", id);
        }

        Target target = resolveTarget(request);

        // Si la sortie reste sur le meme stock, son ancienne quantite est rendue au fut avant controle.
        BigDecimal available = remaining(target.city().getId());
        if (oldCity.getId().equals(target.city().getId())) {
            available = available.add(distribution.getQuantityLiters());
        }
        if (request.quantityLiters().compareTo(available) > 0) {
            throw insufficient(available, target.city());
        }

        boolean sameVehicle = target.vehicle() != null && distribution.getVehicle() != null
                && target.vehicle().getId().equals(distribution.getVehicle().getId());
        distribution.setVehicle(target.vehicle());
        distribution.setExternalPlate(target.externalPlate());
        distribution.setCity(target.city());
        distribution.setDistributionDate(request.distributionDate());
        distribution.setQuantityLiters(request.quantityLiters());
        distribution.setRecipientName(sameVehicle && request.recipientName() != null && !request.recipientName().isBlank()
                ? request.recipientName().trim() : recipient(request.recipientName(), target.vehicle()));
        distribution.setNote(blankToNull(request.note()));
        log.info("Sortie d'huile {} modifiee par {}", id, SecurityUtils.currentUserEmail());
        return buildSummary(target.city().getId());
    }

    // ------------------------------------------------------------------
    // Outils
    // ------------------------------------------------------------------

    private BigDecimal remaining(Long cityId) {
        return purchaseRepository.total(cityId).subtract(distributionRepository.total(cityId));
    }

    /**
     * Camion de la flotte (son stock = celui de sa ville) ou, a defaut, immatriculation hors flotte (stock de la
     * ville du gestionnaire, ou de la ville choisie par l'administrateur). Un camion de la flotte prime.
     */
    private Target resolveTarget(OilDistributionRequest request) {
        if (request.vehicleId() != null) {
            Vehicle vehicle = scopedVehicle(request.vehicleId());
            return new Target(vehicle, null, cityOf(vehicle));
        }
        String plate = blankToNull(request.externalPlate());
        if (plate == null) {
            throw new BusinessException("RG-7.13",
                    "Choisissez un camion de la flotte ou saisissez l'immatriculation du vehicule",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
        return new Target(null, plate.toUpperCase(Locale.ROOT), resolveStockCity(request.cityId()));
    }

    /** Ville du stock : imposee a un gestionnaire (sa ville), a choisir par l'administrateur. */
    private City resolveStockCity(Long requestedCityId) {
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

    private static String label(Target target) {
        return target.vehicle() != null ? target.vehicle().getRegistrationNumber()
                : target.externalPlate() + " (hors flotte)";
    }

    /** Chauffeur affecte au camion de la flotte ; vide pour un vehicule hors flotte sans nom saisi. */
    private String recipient(String requested, Vehicle vehicle) {
        String name = blankToNull(requested);
        if (name != null || vehicle == null) {
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
                                d.getVehicle() == null ? null : d.getVehicle().getId(),
                                d.getVehicle() == null ? d.getExternalPlate() : d.getVehicle().getRegistrationNumber(),
                                d.getVehicle() == null,
                                d.getCity().getId(), d.getCity().getName(), d.getRecipientName(), d.getQuantityLiters(), d.getNote())).toList());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
