package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.enums.MissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long>, JpaSpecificationExecutor<Mission> {

    Optional<Mission> findByMissionNumber(String missionNumber);

    /** Nombre de livraisons (missions terminees) par chauffeur sur une periode — classement "meilleur chauffeur". */
    @Query("""
           SELECT m.driver.id, COUNT(m) FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           GROUP BY m.driver.id
           """)
    List<Object[]> countCompletedByDriver(@Param("from") Instant from, @Param("to") Instant to);

    /** Anti-doublon du scheduler d'automatisation : au plus une mission generee par jour et par modele. */
    boolean existsByMissionAutomationIdAndCreatedAtBetween(Long missionAutomationId, Instant from, Instant to);

    @EntityGraph(attributePaths = {"client", "vehicle", "driver", "serviceType"})
    Page<Mission> findAllBy(Pageable pageable);

    /**
     * Meme liste, restreinte aux missions d'un camion rattache a une ville —
     * filtrage de securite d'un gestionnaire non-administrateur. La ville
     * geree porte sur le camion (son rattachement permanent), pas sur
     * l'origine ou la destination du trajet.
     */
    @EntityGraph(attributePaths = {"client", "vehicle", "driver", "serviceType"})
    Page<Mission> findAllByVehicle_City_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "vehicle", "driver", "serviceType"})
    List<Mission> findByStatusOrderByPlannedStartAsc(MissionStatus status);

    /** Missions d'un chauffeur, les plus recentes d'abord — page "Mes missions" de l'espace chauffeur. */
    @EntityGraph(attributePaths = {"client", "vehicle", "driver", "serviceType"})
    List<Mission> findByDriverIdOrderByPlannedStartDesc(Long driverId);

    /** Derniere mission d'un camion, quel que soit son statut — position de repli quand aucun boitier GPS n'a jamais emis. */
    @EntityGraph(attributePaths = {"agency", "destinationAgency", "originCity", "destinationCity"})
    Optional<Mission> findFirstByVehicleIdOrderByCreatedAtDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"client", "vehicle", "driver", "serviceType"})
    Optional<Mission> findWithDetailsById(Long id);

    /** Mission active d'un camion : au plus une a la fois. */
    @Query("""
           SELECT m FROM Mission m
           WHERE m.vehicle.id = :vehicleId
             AND m.status IN (com.sogeco.fleet.common.enums.MissionStatus.EN_ATTENTE,
                              com.sogeco.fleet.common.enums.MissionStatus.EN_COURS)
           """)
    List<Mission> findActiveByVehicle(@Param("vehicleId") Long vehicleId);

    @Query("""
           SELECT m FROM Mission m
           WHERE m.driver.id = :driverId
             AND m.status IN (com.sogeco.fleet.common.enums.MissionStatus.EN_ATTENTE,
                              com.sogeco.fleet.common.enums.MissionStatus.EN_COURS)
           """)
    List<Mission> findActiveByDriver(@Param("driverId") Long driverId);

    /**
     * Chevauchement de periode pour un camion ou un chauffeur (RG-5.3).
     * Deux missions se chevauchent si l'une commence avant la fin
     * prevue de l'autre et reciproquement.
     */
    @Query("""
           SELECT COUNT(m) FROM Mission m
           WHERE (m.vehicle.id = :vehicleId OR m.driver.id = :driverId)
             AND m.status IN (com.sogeco.fleet.common.enums.MissionStatus.EN_ATTENTE,
                              com.sogeco.fleet.common.enums.MissionStatus.EN_COURS)
             AND (:excludeId IS NULL OR m.id <> :excludeId)
             AND m.plannedStart <= :end
             AND COALESCE(m.plannedArrival, m.plannedStart) >= :start
           """)
    long countOverlapping(@Param("vehicleId") Long vehicleId,
                          @Param("driverId") Long driverId,
                          @Param("start") Instant start,
                          @Param("end") Instant end,
                          @Param("excludeId") Long excludeId);

    /** Missions terminees sans chiffre d'affaires saisi (RG-5.9). */
    @Query("""
           SELECT m FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.serviceType.billable = true
             AND (m.revenueAmount IS NULL OR m.revenueAmount = 0)
             AND m.actualEnd < :limit
           """)
    List<Mission> findCompletedWithoutRevenue(@Param("limit") Instant limit);

    long countByStatus(MissionStatus status);

    @Query("SELECT COUNT(m) FROM Mission m WHERE m.plannedStart >= :from AND m.plannedStart < :to")
    long countInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(m) FROM Mission m WHERE m.status = :status AND m.plannedStart >= :from AND m.plannedStart < :to")
    long countByStatusInPeriod(@Param("status") MissionStatus status,
                               @Param("from") Instant from, @Param("to") Instant to);

    /** Sequence annuelle des numeros de mission. */
    @Query(value = "SELECT COUNT(*) FROM missions WHERE mission_number LIKE :prefix", nativeQuery = true)
    long countByNumberPrefix(@Param("prefix") String prefix);

    @Query("""
           SELECT COALESCE(SUM(m.revenueAmount), 0) FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           """)
    BigDecimal totalRevenue(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT COALESCE(SUM(m.distanceKm), 0) FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           """)
    BigDecimal totalDistance(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Meme total, restreint a un seul camion — repli du "Km du jour" sur la
     * carte quand ce camion n'a pas de boitier GPS (Vehicle.dailyKm reste
     * alors bloque a zero, jamais incremente que par la telematique).
     */
    @Query("""
           SELECT COALESCE(SUM(m.distanceKm), 0) FROM Mission m
           WHERE m.vehicle.id = :vehicleId
             AND m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           """)
    BigDecimal totalDistanceForVehicle(@Param("vehicleId") Long vehicleId,
                                       @Param("from") Instant from, @Param("to") Instant to);

    /** Meme total, restreint a la ville d'affectation du camion (cityId null = pas de filtre). */
    @Query("""
           SELECT COALESCE(SUM(m.distanceKm), 0) FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
             AND (:cityId IS NULL OR m.vehicle.city.id = :cityId)
           """)
    BigDecimal totalDistanceForCity(@Param("from") Instant from, @Param("to") Instant to,
                                    @Param("cityId") Long cityId);

    // ------------------------------------------------------------------
    // Rentabilite (sprint 7) — exploite directement total_cost et
    // margin_amount, colonnes calculees par PostgreSQL : aucun risque
    // qu'un rapport diverge du chiffre affiche sur la fiche mission.
    // ------------------------------------------------------------------

    @EntityGraph(attributePaths = {"vehicle", "driver", "client", "route", "agency", "serviceType"})
    List<Mission> findByStatusAndActualEndBetween(MissionStatus status, Instant from, Instant to);

    @Query("""
           SELECT m.vehicle.id, m.vehicle.registrationNumber,
                  COUNT(m), COALESCE(SUM(m.revenueAmount),0), COALESCE(SUM(m.totalCost),0),
                  COALESCE(SUM(m.marginAmount),0), COALESCE(SUM(m.distanceKm),0)
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           GROUP BY m.vehicle.id, m.vehicle.registrationNumber
           ORDER BY SUM(m.marginAmount) DESC
           """)
    List<Object[]> aggregateProfitabilityByVehicle(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT m.client.id, m.client.companyName,
                  COUNT(m), COALESCE(SUM(m.revenueAmount),0), COALESCE(SUM(m.totalCost),0),
                  COALESCE(SUM(m.marginAmount),0)
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.client IS NOT NULL
             AND m.actualEnd >= :from AND m.actualEnd < :to
           GROUP BY m.client.id, m.client.companyName
           ORDER BY SUM(m.marginAmount) DESC
           """)
    List<Object[]> aggregateProfitabilityByClient(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT m.route.id, m.route.label,
                  COUNT(m), COALESCE(SUM(m.revenueAmount),0), COALESCE(SUM(m.totalCost),0),
                  COALESCE(SUM(m.marginAmount),0), COALESCE(SUM(m.distanceKm),0)
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.route IS NOT NULL
             AND m.actualEnd >= :from AND m.actualEnd < :to
           GROUP BY m.route.id, m.route.label
           ORDER BY SUM(m.marginAmount) DESC
           """)
    List<Object[]> aggregateProfitabilityByRoute(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT m.agency.id, m.agency.name,
                  COUNT(m), COALESCE(SUM(m.revenueAmount),0), COALESCE(SUM(m.totalCost),0),
                  COALESCE(SUM(m.marginAmount),0)
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.agency IS NOT NULL
             AND m.actualEnd >= :from AND m.actualEnd < :to
           GROUP BY m.agency.id, m.agency.name
           ORDER BY SUM(m.marginAmount) DESC
           """)
    List<Object[]> aggregateProfitabilityByAgency(@Param("from") Instant from, @Param("to") Instant to);

    /** Repartition du cout direct par composante, pour l'ecran Rapports — une seule ligne agregee. */
    @Query("""
           SELECT COALESCE(SUM(m.fuelCost),0), COALESCE(SUM(m.tollCost),0),
                  COALESCE(SUM(m.driverCost),0), COALESCE(SUM(m.otherCost),0), COALESCE(SUM(m.missionFeeCost),0)
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           """)
    List<Object[]> aggregateCostComponents(@Param("from") Instant from, @Param("to") Instant to);

    /** Minutes cumulees en mission, pour le taux d'utilisation de la flotte. */
    @Query(value = """
           SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (actual_end - actual_start)) / 60), 0)
           FROM missions
           WHERE status = 'TERMINEE' AND actual_end >= :from AND actual_end < :to
           """, nativeQuery = true)
    Double totalMissionMinutes(@Param("from") Instant from, @Param("to") Instant to);

    /** Lignes brutes (camion, fin reelle, cout), pour la vue Charges par camion/ville/mois. */
    @Query("""
           SELECT m.vehicle.id, m.actualEnd, m.totalCost
           FROM Mission m
           WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
             AND m.actualEnd >= :from AND m.actualEnd < :to
           """)
    List<Object[]> findCostRowsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
