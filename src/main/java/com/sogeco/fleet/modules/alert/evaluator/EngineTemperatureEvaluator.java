package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Exige un boitier lisant le bus CAN. Sans lui, ne se declenche jamais. */
@Component
public class EngineTemperatureEvaluator implements AlertEvaluator {

    @Override
    public AlertType type() {
        return AlertType.TEMPERATURE_MOTEUR;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {
        if (payload.engineTemperature() == null || !rule.matches(payload.engineTemperature())) {
            return Optional.empty();
        }

        return Optional.of(new AlertCandidate(
                "Temperature moteur",
                "Temperature : %s °C — seuil : %s °C. Arret recommande."
                        .formatted(payload.engineTemperature(), rule.getThresholdValue())));
    }
}
