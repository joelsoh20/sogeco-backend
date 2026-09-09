package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.ClaimStatus;
import com.sogeco.fleet.common.enums.DocumentStatus;
import com.sogeco.fleet.common.enums.InspectionResult;
import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.dto.ComplianceStatsResponse;
import com.sogeco.fleet.modules.insurance.dto.DeadlineItem;
import com.sogeco.fleet.modules.insurance.dto.MissingDocumentItem;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Agregats de l'ecran Assurance & Visite technique.
 *
 * L'echeancier unifie est le point notable : il combine des sources
 * qui ne partagent aucune table — polices d'assurance, visites
 * techniques, permis de conduire — en une seule liste triee par
 * urgence, sans forcer ces modeles a fusionner.
 */
@Service
@RequiredArgsConstructor
public class ComplianceAnalyticsService {

    private final InsurancePolicyRepository policyRepository;
    private final TechnicalInspectionRepository inspectionRepository;
    private final ClaimRepository claimRepository;
    private final DriverRepository driverRepository;
    private final CarteBleueRepository carteBleueRepository;
    private final CarteGriseRepository carteGriseRepository;
    private final CarteRoseRepository carteRoseRepository;
    private final TransportLicenseRepository transportLicenseRepository;
    private final VehicleRepository vehicleRepository;
    private final SettingService settingService;

    /** Compteurs de tete : toutes periodes confondues, comme totalClaims/openClaims — pas un rapport mensuel. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public ComplianceStatsResponse stats() {
        return new ComplianceStatsResponse(
                policyRepository.count(),
                policyRepository.countByStatus(PolicyStatus.ACTIVE),
                policyRepository.findByStatusAndEndDateLessThanEqual(
                        PolicyStatus.ACTIVE, LocalDate.now().plusDays(30)).size(),
                claimRepository.count(),
                claimRepository.countByStatus(ClaimStatus.DECLARE)
                        + claimRepository.countByStatus(ClaimStatus.EN_INSTRUCTION),
                claimRepository.totalEstimatedCost(),
                claimRepository.totalReimbursed(),
                inspectionRepository.countByResultNot(InspectionResult.CONFORME));
    }

    /**
     * Echeancier unifie, trie par urgence croissante.
     *
     * Le statut de chaque ligne suit exactement les seuils du module
     * documents (RG-8.2) : au-dela du delai de preavis parametre,
     * VALIDE ; en dessous, A_RENOUVELER ; date depassee, EXPIRE.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<DeadlineItem> unifiedSchedule(int daysAhead) {
        int warningDays = settingService.getInt("alert.expiry_warning_days", 30);
        LocalDate limit = LocalDate.now().plusDays(daysAhead);
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        List<DeadlineItem> items = new ArrayList<>();

        policyRepository.findByStatusAndEndDateLessThanEqual(PolicyStatus.ACTIVE, limit).stream()
                .filter(policy -> cityId == null || policy.getVehicles().stream().anyMatch(v -> inScope(v, cityId)))
                .forEach(policy -> items.add(new DeadlineItem(
                        "ASSURANCE",
                        policy.getId(),
                        policy.coversFleet() ? "Flotte (%d camions)".formatted(policy.getVehicles().size())
                                : policy.getVehicles().stream().findFirst()
                                    .map(v -> v.getRegistrationNumber()).orElse(policy.getPolicyNumber()),
                        "Police " + policy.getPolicyNumber(),
                        policy.getEndDate(),
                        policy.daysRemaining(),
                        statusFor(policy.daysRemaining(), warningDays))));

        inspectionRepository.findByNextInspectionDateLessThanEqual(limit).stream()
                .filter(inspection -> inScope(inspection.getVehicle(), cityId))
                .forEach(inspection -> items.add(new DeadlineItem(
                        "VISITE_TECHNIQUE",
                        inspection.getVehicle().getId(),
                        inspection.getVehicle().getRegistrationNumber(),
                        "Visite technique",
                        inspection.getNextInspectionDate(),
                        inspection.daysUntilNext(),
                        statusFor(inspection.daysUntilNext(), warningDays))));

        driverRepository.findByActiveTrueAndLicenseExpiryDateLessThanEqual(limit).stream()
                .filter(driver -> cityId == null
                        || (driver.getCity() != null && cityId.equals(driver.getCity().getId())))
                .forEach(driver -> items.add(new DeadlineItem(
                        "PERMIS",
                        driver.getId(),
                        driver.getFullName(),
                        "Permis de conduire",
                        driver.getLicenseExpiryDate(),
                        driver.licenseDaysRemaining(),
                        statusFor(driver.licenseDaysRemaining(), warningDays))));

        carteBleueRepository.findByExpiryDateLessThanEqual(limit).stream()
                .filter(carte -> inScope(carte.getVehicle(), cityId))
                .forEach(carte -> items.add(new DeadlineItem(
                        "CARTE_BLEUE",
                        carte.getVehicle().getId(),
                        carte.getVehicle().getRegistrationNumber(),
                        "Carte bleue " + carte.getReceiptNumber(),
                        carte.getExpiryDate(),
                        carte.daysUntilExpiry(),
                        statusFor(carte.daysUntilExpiry(), warningDays))));

        carteGriseRepository.findByExpiryDateLessThanEqual(limit).stream()
                .filter(carte -> inScope(carte.getVehicle(), cityId))
                .forEach(carte -> items.add(new DeadlineItem(
                        "CARTE_GRISE",
                        carte.getVehicle().getId(),
                        carte.getVehicle().getRegistrationNumber(),
                        "Carte grise " + carte.getRegistrationNumber(),
                        carte.getExpiryDate(),
                        carte.daysUntilExpiry(),
                        statusFor(carte.daysUntilExpiry(), warningDays))));

        carteRoseRepository.findByValidToLessThanEqual(limit).stream()
                .filter(carte -> inScope(carte.getVehicle(), cityId))
                .forEach(carte -> items.add(new DeadlineItem(
                        "CARTE_ROSE",
                        carte.getVehicle().getId(),
                        carte.getVehicle().getRegistrationNumber(),
                        "Carte rose " + carte.getRegistrationNumber(),
                        carte.getValidTo(),
                        carte.daysUntilExpiry(),
                        statusFor(carte.daysUntilExpiry(), warningDays))));

        // Licence de transport : couvre la flotte entiere, jamais un camion en
        // particulier — jamais filtree par ville, visible de tout gestionnaire.
        transportLicenseRepository.findByStatusAndExpiryDateLessThanEqual(PolicyStatus.ACTIVE, limit)
                .forEach(license -> items.add(new DeadlineItem(
                        "LICENCE_TRANSPORT",
                        license.getId(),
                        "Flotte entiere",
                        "Licence de transport " + license.getReference(),
                        license.getExpiryDate(),
                        license.daysRemaining(),
                        statusFor(license.daysRemaining(), warningDays))));

        return items.stream().sorted(Comparator.comparing(DeadlineItem::daysRemaining)).toList();
    }

    /**
     * Camions actifs n'ayant JAMAIS eu de document d'un type donne
     * (assurance, visite technique, cartes bleue/grise/rose) — distinct
     * de l'echeancier, qui ne peut lister que des documents deja
     * saisis en train d'arriver a echeance. La licence de transport
     * n'est pas concernee : elle couvre la flotte entiere, pas un
     * camion en particulier.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INSURANCE_READ')")
    public List<MissingDocumentItem> missingDocuments() {
        List<MissingDocumentItem> items = new ArrayList<>();
        Long cityId = SecurityUtils.currentCityId().orElse(null);

        for (Vehicle vehicle : vehicleRepository.findByActiveTrueOrderByRegistrationNumberAsc()) {
            if (!inScope(vehicle, cityId)) {
                continue;
            }
            if (!policyRepository.existsByVehicles_Id(vehicle.getId())) {
                items.add(new MissingDocumentItem("ASSURANCE", vehicle.getId(), vehicle.getRegistrationNumber()));
            }
            if (!inspectionRepository.existsByVehicleId(vehicle.getId())) {
                items.add(new MissingDocumentItem("VISITE_TECHNIQUE", vehicle.getId(), vehicle.getRegistrationNumber()));
            }
            if (!carteBleueRepository.existsByVehicleId(vehicle.getId())) {
                items.add(new MissingDocumentItem("CARTE_BLEUE", vehicle.getId(), vehicle.getRegistrationNumber()));
            }
            if (!carteGriseRepository.existsByVehicleId(vehicle.getId())) {
                items.add(new MissingDocumentItem("CARTE_GRISE", vehicle.getId(), vehicle.getRegistrationNumber()));
            }
            if (!carteRoseRepository.existsByVehicleId(vehicle.getId())) {
                items.add(new MissingDocumentItem("CARTE_ROSE", vehicle.getId(), vehicle.getRegistrationNumber()));
            }
        }

        return items;
    }

    /** Vrai si le camion est dans la ville geree, ou si l'appelant voit tout (cityId null = admin). */
    private boolean inScope(Vehicle vehicle, Long cityId) {
        return cityId == null || (vehicle.getCity() != null && cityId.equals(vehicle.getCity().getId()));
    }

    private DocumentStatus statusFor(Long daysRemaining, int warningDays) {
        if (daysRemaining < 0) return DocumentStatus.EXPIRE;
        if (daysRemaining <= warningDays) return DocumentStatus.A_RENOUVELER;
        return DocumentStatus.VALIDE;
    }
}
