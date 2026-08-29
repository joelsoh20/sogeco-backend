package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Code defaut moteur remonte par le bus CAN, par exemple P0480. */
@Component
public class EngineFaultEvaluator implements AlertEvaluator {

    @Override
    public AlertType type() {
        return AlertType.PANNE_DETECTEE;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {
        if (!payload.hasFault()) {
            return Optional.empty();
        }

        return Optional.of(new AlertCandidate(
                "Panne detectee",
                "Code defaut moteur : %s".formatted(String.join(", ", payload.errorCodes()))));
    }
}
