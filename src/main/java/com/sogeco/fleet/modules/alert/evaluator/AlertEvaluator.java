package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;

import java.util.Optional;

/**
 * Evaluation d'une regle sur une trame.
 *
 * Une implementation par type d'alerte : ajouter une regle revient a
 * ajouter une classe, sans modifier le moteur (patron Strategy).
 */
public interface AlertEvaluator {

    AlertType type();

    /**
     * Retourne une alerte candidate si la regle se declenche.
     * Le contexte porte l'etat precedent du camion, necessaire aux
     * regles qui raisonnent sur une variation.
     */
    Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                      AlertRule rule, EvaluationContext context);
}
