package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SpeedEvaluator implements AlertEvaluator {

    @Override
    public AlertType type() {
        return AlertType.VITESSE_EXCESSIVE;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {
        if (payload.speedKmh() == null || !rule.matches(payload.speedKmh())) {
            return Optional.empty();
        }

        return Optional.of(new AlertCandidate(
                "Vitesse excessive",
                "Vitesse : %s km/h — limite : %s km/h"
                        .formatted(payload.speedKmh(), rule.getThresholdValue())));
    }
}
