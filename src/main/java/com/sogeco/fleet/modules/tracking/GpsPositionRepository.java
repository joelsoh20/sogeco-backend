package com.sogeco.fleet.modules.tracking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GpsPositionRepository extends JpaRepository<GpsPosition, Long> {

    /**
     * Derniere position connue d'un camion.
     *
     * En exploitation courante on lit le cache Redis ; cette requete
     * sert au demarrage a froid et aux verifications.
     */
    @Query("""
           SELECT p FROM GpsPosition p
           WHERE p.vehicleId = :vehicleId
           ORDER BY p.recordedAt DESC
           LIMIT 1
           """)
    Optional<GpsPosition> findLatest(@Param("vehicleId") Long vehicleId);

    /**
     * Dernier releve d'odometre CONNU DU BOITIER pour ce camion (pas le
     * kilometrage interne du camion, qui peut avoir pris du retard sur
     * lui -- panne d'ingestion passee, trames perdues). Sert de reference
     * a TelematicsIngestionService.odometerDistance() pour verifier la
     * vraisemblance d'un nouveau releve : comparer au dernier releve du
     * boitier permet au suivi de rattraper l'ecart des que le boitier
     * revient a jour, plutot que de rester bloque en dessous de la
     * verite terrain indefiniment.
     */
    @Query("""
           SELECT p FROM GpsPosition p
           WHERE p.vehicleId = :vehicleId AND p.odometerKm IS NOT NULL
           ORDER BY p.recordedAt DESC
           LIMIT 1
           """)
    Optional<GpsPosition> findLatestWithOdometer(@Param("vehicleId") Long vehicleId);

    @Query("""
           SELECT p FROM GpsPosition p
           WHERE p.vehicleId = :vehicleId
             AND p.recordedAt >= :from AND p.recordedAt < :to
           ORDER BY p.recordedAt ASC
           """)
    List<GpsPosition> findHistory(@Param("vehicleId") Long vehicleId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to);

    /**
     * Distance parcourue entre deux instants, pour le calcul de
     * consommation carburant (RG-6.2) : somme des segments Haversine
     * deja calcules a l'ingestion.
     */
    @Query("""
           SELECT COALESCE(SUM(p.distanceKm), 0) FROM GpsPosition p
           WHERE p.vehicleId = :vehicleId
             AND p.recordedAt > :from AND p.recordedAt <= :to
           """)
    BigDecimal sumDistance(@Param("vehicleId") Long vehicleId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    /**
     * Debut de la serie en cours de releves A L'ARRET (vitesse sous le seuil), par camion : le premier
     * releve apres le dernier releve en mouvement de la fenetre. Colonnes : camion, debut, et vrai si
     * aucun releve en mouvement n'existe dans la fenetre (la serie dure depuis au moins ce debut).
     */
    @Query(value = """
           SELECT p.vehicle_id, MIN(p.recorded_at), MAX(m.t) IS NULL
           FROM gps_positions p
           LEFT JOIN (SELECT vehicle_id, MAX(recorded_at) AS t FROM gps_positions
                      WHERE recorded_at > :since AND valid AND speed_kmh > :threshold
                      GROUP BY vehicle_id) m ON m.vehicle_id = p.vehicle_id
           WHERE p.recorded_at > :since AND p.valid AND (m.t IS NULL OR p.recorded_at > m.t)
           GROUP BY p.vehicle_id
           """, nativeQuery = true)
    List<Object[]> stoppedStreakStarts(@Param("since") Instant since, @Param("threshold") BigDecimal threshold);

    /** Symetrique : debut de la serie en cours de releves EN MOUVEMENT, apres le dernier releve a l'arret. */
    @Query(value = """
           SELECT p.vehicle_id, MIN(p.recorded_at), MAX(s.t) IS NULL
           FROM gps_positions p
           LEFT JOIN (SELECT vehicle_id, MAX(recorded_at) AS t FROM gps_positions
                      WHERE recorded_at > :since AND valid AND (speed_kmh IS NULL OR speed_kmh <= :threshold)
                      GROUP BY vehicle_id) s ON s.vehicle_id = p.vehicle_id
           WHERE p.recorded_at > :since AND p.valid AND (s.t IS NULL OR p.recorded_at > s.t)
           GROUP BY p.vehicle_id
           """, nativeQuery = true)
    List<Object[]> movingStreakStarts(@Param("since") Instant since, @Param("threshold") BigDecimal threshold);

    Page<GpsPosition> findByVehicleIdOrderByRecordedAtDesc(Long vehicleId, Pageable pageable);

    List<GpsPosition> findByMissionIdOrderByRecordedAtAsc(Long missionId);

    /** Doublon : meme camion, meme horodatage. */
    boolean existsByVehicleIdAndRecordedAt(Long vehicleId, Instant recordedAt);

    /** Agregation journaliere, avant purge de la partition. */
    @Query(value = """
           SELECT vehicle_id,
                  COALESCE(SUM(distance_km), 0)  AS distance,
                  MAX(speed_kmh)                 AS max_speed,
                  AVG(NULLIF(speed_kmh, 0))      AS avg_speed,
                  COUNT(*)                       AS position_count,
                  MIN(recorded_at)               AS first_at,
                  MAX(recorded_at)               AS last_at,
                  COUNT(*) FILTER (WHERE speed_kmh > :idleThreshold) AS moving_count,
                  COUNT(*) FILTER (WHERE speed_kmh <= :idleThreshold) AS idle_count
           FROM gps_positions
           WHERE recorded_at >= :from AND recorded_at < :to
           GROUP BY vehicle_id
           """, nativeQuery = true)
    List<Object[]> aggregateForDay(@Param("from") Instant from,
                                   @Param("to") Instant to,
                                   @Param("idleThreshold") int idleThreshold);

    @Query(value = "SELECT COUNT(*) FROM gps_positions", nativeQuery = true)
    long countAll();

    /** Camions sans position depuis N minutes, pour l'alerte de perte de signal. */
    @Query(value = """
           SELECT v.id FROM vehicles v
           WHERE v.active = true
             AND v.device_id IS NOT NULL
             AND NOT EXISTS (
                 SELECT 1 FROM gps_positions p
                 WHERE p.vehicle_id = v.id AND p.recorded_at > :since)
           """, nativeQuery = true)
    List<Long> findVehiclesSilentSince(@Param("since") Instant since);

    /** Purge : suppression de la partition entierement anterieure a la limite. */
    @Modifying
    @Query(value = "SELECT drop_gps_partitions_before(CAST(:limit AS DATE))", nativeQuery = true)
    void dropPartitionsBefore(@Param("limit") java.time.LocalDate limit);
}
