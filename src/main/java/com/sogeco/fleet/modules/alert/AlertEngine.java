package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.evaluator.AlertCandidate;
import com.sogeco.fleet.modules.alert.evaluator.AlertEvaluator;
import com.sogeco.fleet.modules.alert.evaluator.EvaluationContext;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Moteur d'evaluation des regles temps reel.
 *
 * Les evaluateurs sont injectes par Spring et indexes par type :
 * ajouter une regle revient a ajouter une classe annotee @Component,
 * sans toucher au moteur.
 *
 * Une regle desactivee n'est jamais evaluee : c'est ce qui permet de
 * demarrer avec quatre alertes et d'en activer d'autres apres
 * calibrage sur donnees reelles.
 */
@Slf4j
@Service
public class AlertEngine {

    private final Map<AlertType, AlertEvaluator> evaluators = new EnumMap<>(AlertType.class);
    private final AlertRuleRepository ruleRepository;
    private final AlertService alertService;

    public AlertEngine(List<AlertEvaluator> evaluatorList,
                       AlertRuleRepository ruleRepository,
                       AlertService alertService) {
        evaluatorList.forEach(evaluator -> evaluators.put(evaluator.type(), evaluator));
        this.ruleRepository = ruleRepository;
        this.alertService = alertService;
        log.info("Moteur d'alertes : {} evaluateurs enregistres", evaluators.size());
    }

    /** Evalue toutes les regles temps reel actives sur une trame. */
    @Transactional
    public void evaluate(TelematicsPayload payload, Vehicle vehicle, EvaluationContext context) {

        for (AlertRule rule : ruleRepository.findByActiveTrueOrderByLevelAscLabelAsc()) {

            if (!rule.getAlertType().isRealTime()) {
                continue;
            }

            AlertEvaluator evaluator = evaluators.get(rule.getAlertType());
            if (evaluator == null) {
                continue;
            }

            try {
                Optional<AlertCandidate> candidate = evaluator.evaluate(payload, vehicle, rule, context);

                candidate.ifPresent(found -> alertService.raise(AlertService.AlertRequest.builder()
                        .type(rule.getAlertType())
                        .level(rule.getLevel())
                        .rule(rule)
                        .title(found.title())
                        .description(found.description())
                        .vehicle(vehicle)
                        .mission(context.activeMission())
                        .latitude(payload.latitude())
                        .longitude(payload.longitude())
                        .build()));

            } catch (RuntimeException e) {
                // L'echec d'une regle ne doit jamais interrompre les autres
                // ni l'enregistrement de la position.
                log.error("Evaluation de la regle {} en echec", rule.getAlertType(), e);
            }
        }
    }
}
