package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Niveau de carburant bas. Exige une sonde de reservoir.
 *
 * Si le boitier ne remonte que des litres, le pourcentage est deduit
 * de la capacite du reservoir saisie sur la fiche camion.
 */
@Component
public class LowFuelEvaluator implements AlertEvaluator {

    @Override
    public AlertType type() {
        return AlertType.CARBURANT_BAS;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {
        BigDecimal percent = resolvePercent(payload, vehicle);
        if (percent == null || !rule.matches(percent)) {
            return Optional.empty();
        }

        String liters = payload.fuelLevelLiters() == null
                ? "" : " (%s L)".formatted(payload.fuelLevelLiters());

        return Optional.of(new AlertCandidate(
                "Niveau de carburant bas",
                "Niveau : %s%%%s — seuil : %s%%"
                        .formatted(percent, liters, rule.getThresholdValue())));
    }

    private BigDecimal resolvePercent(TelematicsPayload payload, Vehicle vehicle) {
        if (payload.fuelLevelPercent() != null) {
            return payload.fuelLevelPercent();
        }
        BigDecimal liters = payload.fuelLevelLiters();
        BigDecimal capacity = vehicle.getTankCapacityLiters();
        if (liters == null || capacity == null || capacity.signum() == 0) {
            return null;
        }
        return liters.multiply(BigDecimal.valueOf(100)).divide(capacity, 1, RoundingMode.HALF_UP);
    }
}
