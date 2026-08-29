package com.sogeco.fleet.modules.tracking.simulator;

import com.sogeco.fleet.modules.tracking.TelematicsIngestionService;
import com.sogeco.fleet.modules.tracking.WebhookEvent;
import com.sogeco.fleet.modules.tracking.WebhookEventRepository;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import com.sogeco.fleet.common.enums.WebhookStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulateur de trames, actif sur le profil de developpement.
 *
 * Il permet de valider toute la chaine — ingestion, alertes,
 * georeperage, diffusion — sans attendre l'equipement des camions.
 * Les trajets suivent les trois corridors reels.
 *
 * Les scenarios d'alerte sont declenchables a la demande : c'est le
 * seul moyen de verifier qu'une regle fonctionne avant de la voir se
 * produire en exploitation.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class TelematicsSimulator {

    /** Points de reference des trois corridors, releves sur la carte. */
    private static final double[] DOUALA    = {4.0511, 9.7679};
    private static final double[] YAOUNDE   = {3.8480, 11.5021};
    private static final double[] BAFOUSSAM = {5.4737, 10.4179};

    private static final Random RANDOM = new Random();

    private final VehicleRepository vehicleRepository;
    private final WebhookEventRepository webhookRepository;
    private final TelematicsIngestionService ingestionService;

    public enum Scenario {
        TRAJET_NORMAL,
        EXCES_VITESSE,
        CARBURANT_BAS,
        SIPHONNAGE,
        SURCHAUFFE_MOTEUR,
        CODE_DEFAUT,
        DEMARRAGE_NON_AUTORISE
    }

    public enum Corridor {
        DOUALA_YAOUNDE,
        DOUALA_BAFOUSSAM,
        YAOUNDE_BAFOUSSAM,
        INTRA_DOUALA
    }

    /**
     * Rejoue un trajet complet, position par position.
     * Le nombre de points determine la finesse du trace.
     */
    @Transactional
    public int simulateTrip(Long vehicleId, Corridor corridor, int points, Scenario scenario) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow();

        if (vehicle.getDeviceId() == null) {
            throw new IllegalStateException(
                    "Le camion %s n'a pas d'identifiant de boitier : renseignez deviceId"
                            .formatted(vehicle.getRegistrationNumber()));
        }

        double[] from = origin(corridor);
        double[] to = destination(corridor);

        BigDecimal fuel = BigDecimal.valueOf(scenario == Scenario.CARBURANT_BAS ? 18 : 85);
        Instant clock = Instant.now().minusSeconds(points * 30L);

        for (int i = 0; i < points; i++) {
            double ratio = points == 1 ? 1.0 : (double) i / (points - 1);

            // Interpolation lineaire, avec une legere derive pour que le
            // trace ne soit pas une droite parfaite.
            double latitude = from[0] + (to[0] - from[0]) * ratio + jitter();
            double longitude = from[1] + (to[1] - from[1]) * ratio + jitter();

            fuel = nextFuelLevel(fuel, scenario, i);

            TelematicsPayload payload = TelematicsPayload.builder()
                    .deviceId(vehicle.getDeviceId())
                    .recordedAt(clock.plusSeconds(i * 30L))
                    .latitude(BigDecimal.valueOf(latitude).setScale(7, java.math.RoundingMode.HALF_UP))
                    .longitude(BigDecimal.valueOf(longitude).setScale(7, java.math.RoundingMode.HALF_UP))
                    .speedKmh(speedFor(scenario, i, points))
                    .heading(BigDecimal.valueOf(RANDOM.nextInt(360)))
                    .ignitionOn(scenario != Scenario.DEMARRAGE_NON_AUTORISE || i > 0)
                    .fuelLevelPercent(fuel)
                    .engineTemperature(temperatureFor(scenario))
                    .errorCodes(scenario == Scenario.CODE_DEFAUT ? List.of("P0480") : List.of())
                    .protocol("simulator")
                    .valid(true)
                    .build();

            WebhookEvent event = webhookRepository.save(WebhookEvent.builder()
                    .provider("simulator")
                    .deviceId(vehicle.getDeviceId())
                    .payload("{\"simulated\":true,\"scenario\":\"%s\"}".formatted(scenario))
                    .signatureValid(true)
                    .status(WebhookStatus.RECU)
                    .build());

            ingestionService.process(event.getId(), payload);
        }

        log.info("Simulation {} sur {} : {} positions, scenario {}",
                corridor, vehicle.getRegistrationNumber(), points, scenario);

        return points;
    }

    // ------------------------------------------------------------------

    private BigDecimal speedFor(Scenario scenario, int index, int total) {
        // Depart et arrivee a l'arret, vitesse de croisiere entre les deux.
        if (index == 0 || index == total - 1) {
            return scenario == Scenario.DEMARRAGE_NON_AUTORISE
                    ? BigDecimal.valueOf(45)
                    : BigDecimal.ZERO;
        }
        if (scenario == Scenario.EXCES_VITESSE && index == total / 2) {
            return BigDecimal.valueOf(112);
        }
        return BigDecimal.valueOf(55 + RANDOM.nextInt(25));
    }

    private BigDecimal nextFuelLevel(BigDecimal current, Scenario scenario, int index) {
        if (scenario == Scenario.SIPHONNAGE && index == 5) {
            // Chute brutale : c'est l'amplitude, pas le niveau, qui alerte.
            return current.subtract(BigDecimal.valueOf(25)).max(BigDecimal.ZERO);
        }
        return current.subtract(BigDecimal.valueOf(0.3)).max(BigDecimal.ZERO);
    }

    private BigDecimal temperatureFor(Scenario scenario) {
        return BigDecimal.valueOf(scenario == Scenario.SURCHAUFFE_MOTEUR ? 108 : 88);
    }

    /** Legere derive aleatoire, pour un trace realiste. */
    private double jitter() {
        return (RANDOM.nextDouble() - 0.5) * 0.004;
    }

    private double[] origin(Corridor corridor) {
        return switch (corridor) {
            case DOUALA_YAOUNDE, DOUALA_BAFOUSSAM, INTRA_DOUALA -> DOUALA;
            case YAOUNDE_BAFOUSSAM -> YAOUNDE;
        };
    }

    private double[] destination(Corridor corridor) {
        return switch (corridor) {
            case DOUALA_YAOUNDE -> YAOUNDE;
            case DOUALA_BAFOUSSAM -> BAFOUSSAM;
            case YAOUNDE_BAFOUSSAM -> BAFOUSSAM;
            case INTRA_DOUALA -> new double[]{DOUALA[0] + 0.06, DOUALA[1] + 0.05};
        };
    }
}
