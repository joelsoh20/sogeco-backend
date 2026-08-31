package com.sogeco.fleet.modules.fuel;

import com.sogeco.fleet.common.enums.FuelLogStatus;
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

public interface FuelLogRepository extends JpaRepository<FuelLog, Long>, JpaSpecificationExecutor<FuelLog> {

    @EntityGraph(attributePaths = {"vehicle", "driver", "station"})
    Page<FuelLog> findAllBy(Pageable pageable);

    /** Meme liste, restreinte a la ville du gestionnaire (RG-13.4). */
    @EntityGraph(attributePaths = {"vehicle", "driver", "station"})
    Page<FuelLog> findAllByVehicle_City_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "driver", "station"})
    List<FuelLog> findByVehicleIdOrderByFuelDatetimeDesc(Long vehicleId);

    List<FuelLog> findByMissionId(Long missionId);

    /** Dernier plein complet valide, base du calcul de consommation. */
    @Query("""
           SELECT f FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.fullTank = true
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime < :before
           ORDER BY f.fuelDatetime DESC
           LIMIT 1
           """)
    Optional<FuelLog> findPreviousFullTank(@Param("vehicleId") Long vehicleId,
                                           @Param("before") Instant before);

    /**
     * Tout premier plein enregistre pour ce camion, complet ou partiel.
     * Sert de repere a l'estimation du niveau de reservoir
     * (FuelAnalyticsService.tankLevelFor) quand l'entreprise ne fait
     * jamais de plein complet : findPreviousFullTank ne trouverait alors
     * jamais rien.
     */
    @Query("""
           SELECT f FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
           ORDER BY f.fuelDatetime ASC
           LIMIT 1
           """)
    Optional<FuelLog> findFirstLog(@Param("vehicleId") Long vehicleId);

    /**
     * Plein precedent, complet ou partiel, servant de borne de depart a
     * la fenetre GPS utilisee pour calculer la distance parcourue.
     * exclude permet d'ignorer le plein en cours de modification.
     */
    @Query("""
           SELECT f FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime < :before
             AND (:exclude IS NULL OR f.id <> :exclude)
           ORDER BY f.fuelDatetime DESC
           LIMIT 1
           """)
    Optional<FuelLog> findPreviousLog(@Param("vehicleId") Long vehicleId,
                                      @Param("before") Instant before,
                                      @Param("exclude") Long exclude);

    /**
     * Litres ajoutes depuis un plein complet de reference (exclu) — ce
     * sont necessairement des pleins partiels, sinon un plein complet
     * plus recent aurait ete retenu comme reference par
     * {@link #findPreviousFullTank}. Sert au calcul du niveau de
     * reservoir estime.
     */
    @Query("""
           SELECT COALESCE(SUM(f.quantityLiters), 0) FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime > :after
           """)
    BigDecimal sumQuantitySince(@Param("vehicleId") Long vehicleId, @Param("after") Instant after);

    /** Derniers pleins exploitables, pour la moyenne mobile (RG-6.7). */
    @Query("""
           SELECT f FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.computedConsumption IS NOT NULL
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
           ORDER BY f.fuelDatetime DESC
           """)
    List<FuelLog> findRecentConsumptions(@Param("vehicleId") Long vehicleId, Pageable pageable);

    /**
     * Memes criteres, restreints aux pleins rattaches a une mission dont le
     * tonnage est renseigne -- le filtrage par seuil (part de la capacite
     * du camion) se fait ensuite cote service, non exprimable ici sans
     * recharger le camion pour chaque ligne.
     */
    @Query("""
           SELECT f FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.computedConsumption IS NOT NULL
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.mission IS NOT NULL
             AND f.mission.cargoWeightKg IS NOT NULL
           ORDER BY f.fuelDatetime DESC
           """)
    List<FuelLog> findRecentWithCargoWeight(@Param("vehicleId") Long vehicleId, Pageable pageable);

    @Query("""
           SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           """)
    BigDecimal totalCost(@Param("from") Instant from, @Param("to") Instant to);

    /** Meme total, restreint aux camions affectes a l'une des villes donnees. */
    @Query("""
           SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
             AND f.vehicle.city.name IN :cityNames
           """)
    BigDecimal totalCostForCities(@Param("from") Instant from, @Param("to") Instant to,
                                  @Param("cityNames") List<String> cityNames);

    @Query("""
           SELECT COALESCE(SUM(f.quantityLiters), 0) FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           """)
    BigDecimal totalLiters(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Memes totaux que totalCost/totalLiters, filtres sur la ville
     * d'affectation du camion (cityId null = pas de filtre) — l'ecran
     * Carburant ne charge qu'une ville a la fois pour eviter trop de
     * donnees d'un coup.
     */
    @Query("""
           SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
             AND (:cityId IS NULL OR f.vehicle.city.id = :cityId)
           """)
    BigDecimal totalCostForCity(@Param("from") Instant from, @Param("to") Instant to, @Param("cityId") Long cityId);

    @Query("""
           SELECT COALESCE(SUM(f.quantityLiters), 0) FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
             AND (:cityId IS NULL OR f.vehicle.city.id = :cityId)
           """)
    BigDecimal totalLitersForCity(@Param("from") Instant from, @Param("to") Instant to, @Param("cityId") Long cityId);

    @Query("""
           SELECT COALESCE(SUM(f.totalCost), 0) FROM FuelLog f
           WHERE f.vehicle.id = :vehicleId
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           """)
    BigDecimal totalCostForVehicle(@Param("vehicleId") Long vehicleId,
                                   @Param("from") Instant from, @Param("to") Instant to);

    /** Repartition des couts par camion, pour l'anneau de la maquette. */
    @Query("""
           SELECT f.vehicle.id, f.vehicle.registrationNumber,
                  SUM(f.quantityLiters), SUM(f.totalCost), AVG(f.computedConsumption)
           FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           GROUP BY f.vehicle.id, f.vehicle.registrationNumber
           ORDER BY SUM(f.totalCost) DESC
           """)
    List<Object[]> aggregateByVehicle(@Param("from") Instant from, @Param("to") Instant to);

    /** Meme repartition, restreinte a la ville d'affectation du camion. */
    @Query("""
           SELECT f.vehicle.id, f.vehicle.registrationNumber,
                  SUM(f.quantityLiters), SUM(f.totalCost), AVG(f.computedConsumption)
           FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
             AND (:cityId IS NULL OR f.vehicle.city.id = :cityId)
           GROUP BY f.vehicle.id, f.vehicle.registrationNumber
           ORDER BY SUM(f.totalCost) DESC
           """)
    List<Object[]> aggregateByVehicleForCity(@Param("from") Instant from, @Param("to") Instant to,
                                             @Param("cityId") Long cityId);

    /** Tendance quotidienne de consommation, pour la courbe de la maquette. */
    @Query("""
           SELECT CAST(f.fuelDatetime AS date), COALESCE(SUM(f.quantityLiters), 0)
           FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           GROUP BY CAST(f.fuelDatetime AS date)
           ORDER BY CAST(f.fuelDatetime AS date)
           """)
    List<Object[]> aggregateByDay(@Param("from") Instant from, @Param("to") Instant to);

    /** Tendance mensuelle de consommation (6 derniers mois), restreinte a une ville si precisee. */
    @Query("""
           SELECT FUNCTION('date_trunc', 'month', f.fuelDatetime), COALESCE(SUM(f.quantityLiters), 0)
           FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
             AND (:cityId IS NULL OR f.vehicle.city.id = :cityId)
           GROUP BY FUNCTION('date_trunc', 'month', f.fuelDatetime)
           ORDER BY FUNCTION('date_trunc', 'month', f.fuelDatetime)
           """)
    List<Object[]> aggregateByMonthForCity(@Param("from") Instant from, @Param("to") Instant to,
                                           @Param("cityId") Long cityId);

    /**
     * Classement des chauffeurs par consommation moyenne, du plus
     * economique au moins economique — pour le "Top 5 economie de
     * carburant" de l'ecran Chauffeurs. Filtre sur driver IS NOT NULL :
     * un plein sans chauffeur renseigne (rare, mais possible) ne doit
     * pas fausser un classement individuel.
     */
    @Query("""
           SELECT f.driver.id, CONCAT(f.driver.firstName, ' ', f.driver.lastName),
                  AVG(f.computedConsumption), SUM(f.quantityLiters), SUM(f.totalCost)
           FROM FuelLog f
           WHERE f.driver IS NOT NULL
             AND f.computedConsumption IS NOT NULL
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           GROUP BY f.driver.id, f.driver.firstName, f.driver.lastName
           ORDER BY AVG(f.computedConsumption) ASC
           """)
    List<Object[]> aggregateByDriver(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
           SELECT f.station.id, f.station.name, SUM(f.quantityLiters), SUM(f.totalCost)
           FROM FuelLog f
           WHERE f.station IS NOT NULL
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           GROUP BY f.station.id, f.station.name
           ORDER BY SUM(f.totalCost) DESC
           """)
    List<Object[]> aggregateByStation(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatus(FuelLogStatus status);

    @EntityGraph(attributePaths = {"vehicle", "driver"})
    List<FuelLog> findByStatusOrderByFuelDatetimeDesc(FuelLogStatus status);

    /** Meme liste, restreinte a la ville du gestionnaire (RG-13.4). */
    @EntityGraph(attributePaths = {"vehicle", "driver"})
    List<FuelLog> findByStatusAndVehicle_City_IdOrderByFuelDatetimeDesc(FuelLogStatus status, Long cityId);

    /** Consommation moyenne d'un chauffeur, pour la notation. */
    @Query("""
           SELECT AVG(f.computedConsumption) FROM FuelLog f
           WHERE f.driver.id = :driverId
             AND f.computedConsumption IS NOT NULL
             AND f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           """)
    BigDecimal averageConsumptionForDriver(@Param("driverId") Long driverId,
                                           @Param("from") Instant from, @Param("to") Instant to);

    /**
     * Lignes brutes (camion, date, cout) des pleins SANS mission rattachee, pour la vue
     * Charges par camion/ville/mois. Un plein lie a une mission (RG-6.9) est deja compte
     * via mission.fuelCost -> mission.totalCost ; ne retenir ici que le carburant hors
     * mission evite de le compter deux fois.
     */
    @Query("""
           SELECT f.vehicle.id, f.fuelDatetime, f.totalCost
           FROM FuelLog f
           WHERE f.status <> com.sogeco.fleet.common.enums.FuelLogStatus.ANNULE
             AND f.mission IS NULL
             AND f.fuelDatetime >= :from AND f.fuelDatetime < :to
           """)
    List<Object[]> findStandaloneCostRowsBetween(@Param("from") Instant from, @Param("to") Instant to);
}
