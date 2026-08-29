package com.sogeco.fleet.modules.alert.evaluator;

import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.tracking.dto.LivePosition;

/**
 * Contexte d'evaluation : etat precedent du camion et mission en cours.
 *
 * Certaines regles ne se jugent que sur une variation — un siphonnage
 * est une CHUTE du niveau, pas un niveau bas.
 */
public record EvaluationContext(LivePosition previous, Mission activeMission) {

    public boolean hasActiveMission() {
        return activeMission != null;
    }
}
