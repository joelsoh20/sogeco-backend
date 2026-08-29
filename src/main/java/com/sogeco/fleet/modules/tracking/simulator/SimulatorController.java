package com.sogeco.fleet.modules.tracking.simulator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Pilotage du simulateur. Actif en developpement uniquement : le
 * bean n'existe pas sur le profil de production, l'endpoint non plus.
 */
@RestController
@RequestMapping("/api/v1/simulator")
@Profile("dev")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Simulateur", description = "Generation de trames de test — developpement uniquement")
public class SimulatorController {

    private final TelematicsSimulator simulator;

    @PostMapping("/trip")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Rejouer un trajet et declencher un scenario d'alerte")
    public Map<String, Object> simulate(
            @RequestParam Long vehicleId,
            @RequestParam(defaultValue = "DOUALA_YAOUNDE") TelematicsSimulator.Corridor corridor,
            @RequestParam(defaultValue = "20") int points,
            @RequestParam(defaultValue = "TRAJET_NORMAL") TelematicsSimulator.Scenario scenario) {

        int generated = simulator.simulateTrip(vehicleId, corridor, points, scenario);

        return Map.of(
                "status", "accepted",
                "positions", generated,
                "corridor", corridor,
                "scenario", scenario,
                "note", "Traitement asynchrone : patientez quelques secondes avant de consulter");
    }
}
