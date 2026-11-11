package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.Alert;
import com.sogeco.fleet.modules.alert.AlertRepository;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.alert.AlertRuleRepository;
import com.sogeco.fleet.modules.alert.AlertService;
import com.sogeco.fleet.modules.insurance.dto.DeadlineItem;
import com.sogeco.fleet.modules.insurance.dto.MissingDocumentItem;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notifications pour tout document expire ou manquant de l'ecran Assurance & Visite technique.
 *
 * Une alerte par camion et par nature, qui liste TOUS ses documents concernes avec l'information utile
 * (type, reference, date, nombre de jours de retard) :
 *  - DOCUMENT_EXPIRE : assurance, visite technique, carte bleue / grise / rose dont la date est depassee ;
 *  - DOCUMENT_MANQUANT : documents jamais enregistres pour un camion actif.
 * La licence de transport (qui couvre toute la flotte) donne une alerte sans camion.
 *
 * Evaluee chaque jour avec les autres echeances, et au demarrage (un deploiement notifie aussitot). Une alerte
 * encore ouverte voit sa liste mise a jour ; elle est close automatiquement quand plus aucun document du camion
 * n'est concerne. Niveaux, destinataires et activation viennent des regles d'alerte (ecran Parametres).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceDocumentAlerts {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> LABELS = Map.of(
            "ASSURANCE", "Assurance", "VISITE_TECHNIQUE", "Visite technique", "CARTE_BLEUE", "Carte bleue",
            "CARTE_GRISE", "Carte grise", "CARTE_ROSE", "Carte rose");

    private final ComplianceAnalyticsService analytics;
    private final InsurancePolicyService policyService;
    private final VehicleRepository vehicleRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertService alertService;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            evaluate();
        } catch (Exception e) {
            log.warn("Alertes documents non evaluees au demarrage : {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 35 6 * * *", zone = "Africa/Douala")
    public void scheduled() {
        evaluate();
    }

    @Transactional
    public void evaluate() {
        evaluateExpired();
        evaluateMissing();
    }

    // ------------------------------------------------------------------

    private void evaluateExpired() {
        AlertRule rule = ruleRepository.findByAlertTypeAndActiveTrue(AlertType.DOCUMENT_EXPIRE).orElse(null);
        if (rule == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        Map<Long, List<String>> byVehicle = new LinkedHashMap<>();

        // Assurance : la police couvre un ou plusieurs camions, chacun est concerne.
        for (InsurancePolicy policy : policyService.findExpiringBefore(today)) {
            if (policy.daysRemaining() >= 0) {
                continue;
            }
            String line = "Assurance expirée depuis %d j (police %s, le %s)".formatted(
                    -policy.daysRemaining(), policy.getPolicyNumber(), DATE.format(policy.getEndDate()));
            policy.getVehicles().forEach(v -> byVehicle.computeIfAbsent(v.getId(), k -> new ArrayList<>()).add(line));
        }

        List<DeadlineItem> fleetLevel = new ArrayList<>();
        for (DeadlineItem item : analytics.expiredDocumentsForAlerts()) {
            switch (item.category()) {
                case "VISITE_TECHNIQUE", "CARTE_BLEUE", "CARTE_GRISE", "CARTE_ROSE" ->
                        byVehicle.computeIfAbsent(item.entityId(), k -> new ArrayList<>()).add(
                                "%s expirée depuis %d j (le %s)".formatted(
                                        item.documentLabel(), -item.daysRemaining(), DATE.format(item.dueDate())));
                case "LICENCE_TRANSPORT" -> fleetLevel.add(item);
                default -> {
                    // permis : deja couvert par PERMIS_ECHEANCE
                }
            }
        }

        Set<Long> alerted = new HashSet<>();
        byVehicle.forEach((vehicleId, lines) -> vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            alerted.add(vehicleId);
            raise(rule, AlertType.DOCUMENT_EXPIRE, vehicle,
                    "Document expiré — " + vehicle.getRegistrationNumber(), String.join(" ; ", lines));
        }));

        boolean fleetAlert = !fleetLevel.isEmpty();
        if (fleetAlert) {
            String text = fleetLevel.stream().map(i -> "%s expirée depuis %d j (le %s)".formatted(
                    i.documentLabel(), -i.daysRemaining(), DATE.format(i.dueDate()))).reduce((a, b) -> a + " ; " + b).orElse("");
            raise(rule, AlertType.DOCUMENT_EXPIRE, null, "Document expiré — flotte entière", text);
        }
        closeStale(AlertType.DOCUMENT_EXPIRE, alerted, fleetAlert, "Document(s) renouvelé(s)");
        if (!byVehicle.isEmpty() || fleetAlert) {
            log.info("Documents expires : {} camion(s){}", byVehicle.size(), fleetAlert ? " + licence de transport" : "");
        }
    }

    private void evaluateMissing() {
        AlertRule rule = ruleRepository.findByAlertTypeAndActiveTrue(AlertType.DOCUMENT_MANQUANT).orElse(null);
        if (rule == null) {
            return;
        }
        Map<Long, List<String>> byVehicle = new LinkedHashMap<>();
        for (MissingDocumentItem item : analytics.missingDocumentsForAlerts()) {
            byVehicle.computeIfAbsent(item.vehicleId(), k -> new ArrayList<>())
                    .add(LABELS.getOrDefault(item.category(), item.category()));
        }

        Set<Long> alerted = new HashSet<>();
        byVehicle.forEach((vehicleId, labels) -> vehicleRepository.findById(vehicleId).ifPresent(vehicle -> {
            alerted.add(vehicleId);
            raise(rule, AlertType.DOCUMENT_MANQUANT, vehicle,
                    "Document manquant — " + vehicle.getRegistrationNumber(),
                    "Aucun document enregistré : " + String.join(", ", labels));
        }));
        closeStale(AlertType.DOCUMENT_MANQUANT, alerted, false, "Documents enregistrés");
        if (!byVehicle.isEmpty()) {
            log.info("Documents manquants : {} camion(s)", byVehicle.size());
        }
    }

    private void raise(AlertRule rule, AlertType type, Vehicle vehicle, String title, String description) {
        String text = description.length() > 500 ? description.substring(0, 497) + "..." : description;
        if (vehicle == null && alertRepository.findAllOpen(type).stream().anyMatch(a -> a.getVehicle() == null)) {
            // Alerte « flotte » deja ouverte : mise a jour du texte, sans doublon quotidien.
            alertRepository.findAllOpen(type).stream().filter(a -> a.getVehicle() == null).forEach(a -> {
                a.setTitle(title);
                a.setDescription(text);
            });
            return;
        }
        alertService.raise(AlertService.AlertRequest.builder()
                .type(type)
                .level(rule.getLevel() == null ? AlertLevel.IMPORTANT : rule.getLevel())
                .rule(rule)
                .vehicle(vehicle)
                .title(title)
                .description(text)
                .refreshDescription(true)
                .build());
    }

    /** Clot les alertes ouvertes dont le camion n'a plus aucun document concerne. */
    private void closeStale(AlertType type, Set<Long> stillAlerted, boolean fleetStillAlerted, String note) {
        for (Alert alert : alertRepository.findAllOpen(type)) {
            boolean stale = alert.getVehicle() == null
                    ? !fleetStillAlerted
                    : !stillAlerted.contains(alert.getVehicle().getId());
            if (stale) {
                alertService.autoResolve(alert, note);
            }
        }
    }
}
