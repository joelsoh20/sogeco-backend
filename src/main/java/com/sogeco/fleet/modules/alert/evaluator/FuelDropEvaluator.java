package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Siphonnage : chute brutale du niveau entre deux trames.
 *
 * Une baisse progressive est une consommation normale ; c'est
 * l'amplitude sur un intervalle court qui trahit le detournement.
 * D'ou l'usage du contexte : cette regle ne se juge pas sur une valeur
 * mais sur une variation.
 */
@Component
public class FuelDropEvaluator implements AlertEvaluator {

    @Override
    public AlertType type() {
        return AlertType.SIPHONNAGE;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {

        if (context.previous() == null
                || payload.fuelLevelPercent() == null
                || context.previous().fuelLevelPercent() == null) {
            return Optional.empty();
        }

        BigDecimal drop = context.previous().fuelLevelPercent().subtract(payload.fuelLevelPercent());
        if (drop.signum() <= 0 || !rule.matches(drop)) {
            return Optional.empty();
        }

        // Un remplissage se traduit par une hausse, jamais par une chute :
        // pas de risque de confondre avec un plein.
        return Optional.of(new AlertCandidate(
                "Chute anormale du niveau de carburant",
                "Baisse de %s points (%s%% vers %s%%) sans ravitaillement enregistre"
                        .formatted(drop, context.previous().fuelLevelPercent(), payload.fuelLevelPercent())));
    }
}
