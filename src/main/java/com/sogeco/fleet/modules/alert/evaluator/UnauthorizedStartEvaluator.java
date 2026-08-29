package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRule;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Demarrage hors mission planifiee.
 *
 * Le contact mis, le camion en mouvement, et aucune mission en cours :
 * c'est le scenario d'usage personnel du vehicule.
 *
 * Un simple contact a l'arret ne suffit pas — un chauffeur peut
 * demarrer pour faire tourner la climatisation.
 */
@Component
public class UnauthorizedStartEvaluator implements AlertEvaluator {

    private static final BigDecimal MOVING_THRESHOLD = BigDecimal.valueOf(10);

    @Override
    public AlertType type() {
        return AlertType.DEMARRAGE_NON_AUTORISE;
    }

    @Override
    public Optional<AlertCandidate> evaluate(TelematicsPayload payload, Vehicle vehicle,
                                             AlertRule rule, EvaluationContext context) {

        if (context.hasActiveMission()) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(payload.ignitionOn())) {
            return Optional.empty();
        }
        if (payload.speedKmh() == null || payload.speedKmh().compareTo(MOVING_THRESHOLD) < 0) {
            return Optional.empty();
        }

        return Optional.of(new AlertCandidate(
                "Demarrage non autorise",
                "Le camion circule a %s km/h sans mission planifiee".formatted(payload.speedKmh())));
    }
}
