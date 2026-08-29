package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.common.event.AlertTriggeredEvent;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.alert.dto.AlertResponse;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Creation et cycle de vie des alertes.
 *
 * Le point central est la deduplication : une meme anomalie dans la
 * fenetre de cooldown incremente un compteur au lieu de creer une
 * ligne. Sans cela, un camion coince a 95 km/h pendant une heure
 * generait deux cents alertes, et plus personne ne les lirait.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository repository;
    private final AlertRuleRepository ruleRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Builder
    public record AlertRequest(
            AlertType type,
            AlertLevel level,
            String title,
            String description,
            Vehicle vehicle,
            Driver driver,
            Mission mission,
            BigDecimal latitude,
            BigDecimal longitude,
            String locationLabel,
            AlertRule rule
    ) {
    }

    /**
     * Cree l'alerte, ou incremente celle qui existe deja.
     *
     * Deux gardes-fous, dans l'ordre : une alerte encore ouverte
     * absorbe toujours l'occurrence, quel que soit son age — c'est la
     * meme anomalie tant que personne ne l'a traitee (RG : "juste un
     * rappel, pas une nouvelle alerte a chaque detection"). Une fois
     * fermee, le cooldown de la regle (AlertRule.cooldownMinutes)
     * retarde seulement la reouverture d'une alerte toute juste
     * cloturee, pour eviter le "flapping" (une meme anomalie qui
     * reapparait 5 minutes apres avoir ete resolue).
     */
    @Transactional
    public Optional<Alert> raise(AlertRequest request) {
        AlertRule rule = request.rule() != null
                ? request.rule()
                : ruleRepository.findByAlertType(request.type()).orElse(null);

        int cooldown = rule == null ? 30 : rule.getCooldownMinutes();
        Long vehicleId = request.vehicle() == null ? null : request.vehicle().getId();

        if (vehicleId != null) {
            Optional<Alert> open = repository.findOpen(request.type(), vehicleId);
            if (open.isPresent()) {
                Alert alert = open.get();
                alert.registerOccurrence();
                log.debug("Alerte {} sur {} absorbee, {} occurrences",
                        request.type(), vehicleId, alert.getOccurrences());
                return Optional.empty();
            }

            Optional<Alert> recentlyClosed = repository.findRecentlyClosed(
                    request.type(), vehicleId, Instant.now().minusSeconds(cooldown * 60L));
            if (recentlyClosed.isPresent()) {
                log.debug("Alerte {} sur {} ignoree : cloturee il y a moins de {} min",
                        request.type(), vehicleId, cooldown);
                return Optional.empty();
            }
        }

        Alert alert = repository.save(Alert.builder()
                .rule(rule)
                .alertType(request.type())
                .level(request.level() != null ? request.level()
                        : rule != null ? rule.getLevel() : AlertLevel.IMPORTANT)
                .title(request.title())
                .description(request.description())
                .vehicle(request.vehicle())
                .driver(request.driver())
                .mission(request.mission())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .locationLabel(request.locationLabel())
                .status(AlertStatus.NON_RESOLUE)
                .triggeredAt(Instant.now())
                .build());

        log.info("Alerte {} [{}] : {}", alert.getAlertType(), alert.getLevel(), alert.getTitle());
        events.publishEvent(new AlertTriggeredEvent(alert.getId()));

        return Optional.of(alert);
    }

    // ------------------------------------------------------------------
    // Traitement
    // ------------------------------------------------------------------

    /**
     * Construit le DTO ici, pendant que la session Hibernate est encore
     * ouverte : open-in-view est desactive (application.yml), donc
     * renvoyer l'entite brute au controleur pour la convertir ensuite
     * declenche une LazyInitializationException des qu'il accede a
     * vehicle/driver.
     */
    @Transactional
    public AlertResponse acknowledge(Long id) {
        Alert alert = find(id);
        alert.acknowledge(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null));
        return AlertResponse.from(alert);
    }

    @Transactional
    public AlertResponse resolve(Long id, String note) {
        Alert alert = find(id);
        alert.resolve(
                SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null),
                note);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.ALERT_RESOLVED,
                "Alert", id, null, null, note == null ? null : "\"%s\"".formatted(note));

        return AlertResponse.from(alert);
    }

    @Transactional
    public AlertResponse ignore(Long id, String note) {
        Alert alert = find(id);
        alert.setStatus(AlertStatus.IGNOREE);
        alert.setResolutionNote(note);
        alert.setResolvedAt(Instant.now());
        alert.setResolvedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null));
        return AlertResponse.from(alert);
    }

    @Transactional(readOnly = true)
    public Alert find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte", id));
    }
}
