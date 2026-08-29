package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.alert.AlertRuleRepository;
import com.sogeco.fleet.modules.alert.AlertService;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.driver.DriverRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Evaluation quotidienne des trois regles d'alerte differees
 * ASSURANCE_ECHEANCE, VISITE_TECHNIQUE_ECHEANCE, PERMIS_ECHEANCE.
 *
 * Ces regles existent en base et sont actives depuis le sprint 5
 * (AlertType.isRealTime() == false pour les trois), mais rien ne les
 * evaluait faute de module conformite pour fournir les dates
 * d'echeance. Cette tache ferme cette boucle.
 *
 * Le seuil vient du champ threshold_value de la regle elle-meme
 * (30 jours par defaut) : le modifier depuis l'ecran Parametres change
 * immediatement le comportement de cette tache, sans redeploiement.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceScheduler {

    private final InsurancePolicyService policyService;
    private final TechnicalInspectionService inspectionService;
    private final DriverRepository driverRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertService alertService;

    @Scheduled(cron = "0 30 6 * * *", zone = "Africa/Douala")
    @Transactional
    public void evaluateDeadlines() {
        evaluateInsurance();
        evaluateInspections();
        evaluateLicenses();
    }

    private void evaluateInsurance() {
        ruleRepository.findByAlertTypeAndActiveTrue(AlertType.ASSURANCE_ECHEANCE).ifPresent(rule -> {
            LocalDate limit = LocalDate.now().plusDays(thresholdDays(rule));
            var expiring = policyService.findExpiringBefore(limit);

            expiring.forEach(policy -> policy.getVehicles().forEach(vehicle ->
                    alertService.raise(AlertService.AlertRequest.builder()
                            .type(AlertType.ASSURANCE_ECHEANCE)
                            .level(rule.getLevel())
                            .rule(rule)
                            .vehicle(vehicle)
                            .title("Assurance a echeance")
                            .description("Police %s : echeance le %s (dans %d jours)".formatted(
                                    policy.getPolicyNumber(), policy.getEndDate(), policy.daysRemaining()))
                            .build())));

            if (!expiring.isEmpty()) {
                log.info("{} police(s) d'assurance a echeance sous {} jours", expiring.size(), thresholdDays(rule));
            }
        });
    }

    private void evaluateInspections() {
        ruleRepository.findByAlertTypeAndActiveTrue(AlertType.VISITE_TECHNIQUE_ECHEANCE).ifPresent(rule -> {
            LocalDate limit = LocalDate.now().plusDays(thresholdDays(rule));
            var expiring = inspectionService.findExpiringBefore(limit);

            expiring.forEach(inspection -> alertService.raise(AlertService.AlertRequest.builder()
                    .type(AlertType.VISITE_TECHNIQUE_ECHEANCE)
                    .level(rule.getLevel())
                    .rule(rule)
                    .vehicle(inspection.getVehicle())
                    .title("Visite technique a echeance")
                    .description("Echeance le %s (dans %d jours)".formatted(
                            inspection.getNextInspectionDate(), inspection.daysUntilNext()))
                    .build()));

            if (!expiring.isEmpty()) {
                log.info("{} visite(s) technique(s) a echeance sous {} jours", expiring.size(), thresholdDays(rule));
            }
        });
    }

    private void evaluateLicenses() {
        ruleRepository.findByAlertTypeAndActiveTrue(AlertType.PERMIS_ECHEANCE).ifPresent(rule -> {
            LocalDate limit = LocalDate.now().plusDays(thresholdDays(rule));
            var expiring = driverRepository.findByActiveTrueAndLicenseExpiryDateLessThanEqual(limit);

            for (Driver driver : expiring) {
                alertService.raise(AlertService.AlertRequest.builder()
                        .type(AlertType.PERMIS_ECHEANCE)
                        .level(rule.getLevel())
                        .rule(rule)
                        .driver(driver)
                        .title("Permis de conduire a echeance")
                        .description("%s : echeance le %s (dans %d jours)".formatted(
                                driver.getFullName(), driver.getLicenseExpiryDate(),
                                driver.licenseDaysRemaining()))
                        .build());
            }

            if (!expiring.isEmpty()) {
                log.info("{} permis a echeance sous {} jours", expiring.size(), thresholdDays(rule));
            }
        });
    }

    private int thresholdDays(AlertRule rule) {
        return rule.getThresholdValue() == null ? 30 : rule.getThresholdValue().intValue();
    }
}
