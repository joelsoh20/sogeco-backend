package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    @EntityGraph(attributePaths = {"vehicle", "driver", "rule"})
    Page<Alert> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "driver", "rule"})
    Page<Alert> findByLevelOrderByTriggeredAtDesc(AlertLevel level, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "driver"})
    List<Alert> findByStatusInOrderByTriggeredAtDesc(List<AlertStatus> statuses, Pageable pageable);

    /**
     * Alerte encore ouverte de meme nature sur le meme camion, quelle
     * que soit son anciennete : tant qu'elle n'est ni resolue ni
     * ignoree, c'est la meme anomalie en cours — on incremente au lieu
     * d'en creer une seconde. Sans borne de temps ici : une alerte
     * persistante (perte de signal, par exemple) ne doit jamais se
     * dupliquer simplement parce qu'elle reste ouverte longtemps.
     */
    @Query("""
           SELECT a FROM Alert a
           WHERE a.alertType = :type
             AND a.vehicle.id = :vehicleId
             AND a.status IN (com.sogeco.fleet.common.enums.AlertStatus.NON_RESOLUE,
                              com.sogeco.fleet.common.enums.AlertStatus.EN_COURS)
           ORDER BY a.triggeredAt DESC
           LIMIT 1
           """)
    Optional<Alert> findOpen(@Param("type") AlertType type, @Param("vehicleId") Long vehicleId);

    /**
     * Derniere alerte cloturee (resolue ou ignoree) de meme nature sur
     * le meme camion, si la cloture date de moins de :since — delai
     * anti-repetition (AlertRule.cooldownMinutes) : evite qu'une
     * anomalie qui reapparait juste apres cloture ne reouvre aussitot
     * une nouvelle alerte.
     */
    @Query("""
           SELECT a FROM Alert a
           WHERE a.alertType = :type
             AND a.vehicle.id = :vehicleId
             AND a.status IN (com.sogeco.fleet.common.enums.AlertStatus.RESOLUE,
                              com.sogeco.fleet.common.enums.AlertStatus.IGNOREE)
             AND a.resolvedAt > :since
           ORDER BY a.resolvedAt DESC
           LIMIT 1
           """)
    Optional<Alert> findRecentlyClosed(@Param("type") AlertType type,
                                       @Param("vehicleId") Long vehicleId,
                                       @Param("since") Instant since);

    long countByStatusIn(List<AlertStatus> statuses);

    long countByLevelAndStatusIn(AlertLevel level, List<AlertStatus> statuses);

    @Query("""
           SELECT a.alertType, COUNT(a) FROM Alert a
           WHERE a.triggeredAt >= :from AND a.triggeredAt < :to
           GROUP BY a.alertType
           ORDER BY COUNT(a) DESC
           """)
    List<Object[]> countByType(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT a.status, COUNT(a) FROM Alert a
           WHERE a.triggeredAt >= :from AND a.triggeredAt < :to
           GROUP BY a.status
           """)
    List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

    /** Delai moyen de resolution, en minutes. */
    @Query(value = """
           SELECT AVG(EXTRACT(EPOCH FROM (resolved_at - triggered_at)) / 60)
           FROM alerts
           WHERE resolved_at IS NOT NULL
             AND triggered_at >= :from AND triggered_at < :to
           """, nativeQuery = true)
    Double averageResolutionMinutes(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"vehicle", "driver"})
    List<Alert> findTop10ByStatusInOrderByTriggeredAtDesc(List<AlertStatus> statuses);

    /** Alertes recentes liees a un chauffeur precis — ecran Chauffeurs et Performance. */
    @EntityGraph(attributePaths = {"vehicle", "driver"})
    List<Alert> findTop10ByDriverIdOrderByTriggeredAtDesc(Long driverId);
}
