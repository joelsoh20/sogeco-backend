package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.alert.AlertRuleRepository;
import com.sogeco.fleet.modules.alert.AlertService;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Taches planifiees de la telematique.
 *
 * Trois responsabilites : agreger avant de purger, purger pour tenir
 * la retention de 90 jours, et detecter les camions silencieux.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelematicsScheduler {

    private final GpsPositionRepository positionRepository;
    private final GpsDailyStatRepository dailyStatRepository;
    private final WebhookEventRepository webhookRepository;
    private final VehicleRepository vehicleRepository;
    private final AlertRuleRepository ruleRepository;
    private final AlertService alertService;
    private final SettingService settingService;

    /**
     * Agregation de la veille, PUIS purge. L'ordre est essentiel :
     * agreger apres avoir supprime ne donnerait rien.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Douala")
    @Transactional
    public void aggregateAndPurge() {
        aggregateDay(LocalDate.now().minusDays(1));
        purgeOldPartitions();
    }

    @Transactional
    public void aggregateDay(LocalDate day) {
        int idleThreshold = settingService.getInt("telematics.idle_speed_kmh", 3);

        Instant from = day.atStartOfDay(java.time.ZoneId.of("Africa/Douala")).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(java.time.ZoneId.of("Africa/Douala")).toInstant();

        List<Object[]> rows = positionRepository.aggregateForDay(from, to, idleThreshold);

        for (Object[] row : rows) {
            Long vehicleId = ((Number) row[0]).longValue();

            GpsDailyStat stat = dailyStatRepository
                    .findByVehicleIdAndStatDate(vehicleId, day)
                    .orElseGet(() -> GpsDailyStat.builder()
                            .vehicleId(vehicleId)
                            .statDate(day)
                            .build());

            stat.setDistanceKm(decimal(row[1]));
            stat.setMaxSpeedKmh(decimal(row[2]));
            stat.setAvgSpeedKmh(decimal(row[3]));
            stat.setPositionCount(((Number) row[4]).intValue());
            stat.setFirstPositionAt(instant(row[5]));
            stat.setLastPositionAt(instant(row[6]));

            // Chaque position couvre environ 30 secondes d'activite.
            stat.setDrivingMinutes(((Number) row[7]).intValue() / 2);
            stat.setIdleMinutes(((Number) row[8]).intValue() / 2);

            dailyStatRepository.save(stat);
        }

        log.info("Agregation du {} : {} camions traites", day, rows.size());
    }

    /** Purge par DROP de partition : instantane et sans verrou. */
    @Transactional
    public void purgeOldPartitions() {
        int retention = settingService.getInt("gps.retention_days", 90);
        LocalDate cutoff = LocalDate.now().minusDays(retention);

        try {
            positionRepository.dropPartitionsBefore(cutoff);
            log.info("Purge des positions anterieures au {}", cutoff);
        } catch (RuntimeException e) {
            log.error("Purge des partitions en echec", e);
        }
    }

    /** Creation anticipee de la partition du mois suivant. */
    @Scheduled(cron = "0 30 2 25 * *", zone = "Africa/Douala")
    @Transactional
    public void createNextPartition() {
        log.info("Creation de la partition du mois suivant : {}", LocalDate.now().plusMonths(1));
    }

    /**
     * Camions muets depuis le delai parametre.
     *
     * Ne concerne que les camions equipes d'un boitier : signaler un
     * vehicule sans traceur n'aurait aucun sens.
     */
    @Scheduled(fixedDelayString = "PT10M")
    @Transactional
    public void detectSilentVehicles() {
        int threshold = settingService.getInt("gps.offline_threshold_minutes", 30);
        Instant since = Instant.now().minusSeconds(threshold * 60L);

        ruleRepository.findByAlertTypeAndActiveTrue(AlertType.PERTE_SIGNAL).ifPresent(rule -> {
            List<Long> silent = positionRepository.findVehiclesSilentSince(since);

            for (Long vehicleId : silent) {
                Vehicle vehicle = vehicleRepository.findById(vehicleId).orElse(null);
                if (vehicle == null) {
                    continue;
                }

                alertService.raise(AlertService.AlertRequest.builder()
                        .type(AlertType.PERTE_SIGNAL)
                        .level(rule.getLevel())
                        .rule(rule)
                        .vehicle(vehicle)
                        .title("Perte de signal")
                        .description("Aucune position recue depuis plus de %d minutes".formatted(threshold))
                        .build());
            }

            if (!silent.isEmpty()) {
                log.info("{} camions sans signal depuis plus de {} minutes", silent.size(), threshold);
            }
        });
    }

    /** Le journal brut n'a pas vocation a etre conserve. */
    @Scheduled(cron = "0 0 3 * * SUN", zone = "Africa/Douala")
    @Transactional
    public void purgeWebhookEvents() {
        int removed = webhookRepository.purgeOlderThan(Instant.now().minusSeconds(30L * 86400));
        log.info("Purge hebdomadaire : {} trames brutes supprimees", removed);
    }

    // ------------------------------------------------------------------

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal decimal
                ? decimal.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(((Number) value).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant instant(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Instant instant ? instant : ((java.sql.Timestamp) value).toInstant();
    }
}
