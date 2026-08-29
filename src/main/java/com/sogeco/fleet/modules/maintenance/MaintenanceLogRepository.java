package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MaintenanceLogRepository
        extends JpaRepository<MaintenanceLog, Long>, JpaSpecificationExecutor<MaintenanceLog> {

    /**
     * Pannes par chauffeur sur une periode, pour le classement "meilleur
     * chauffeur" (le moins de pannes). Une panne (MaintenanceLog) ne
     * connait que le camion, pas le chauffeur : on retrouve qui
     * conduisait a la date de l'intervention via l'affectation active a
     * ce moment-la (vehicle_assignments). Une panne survenue hors de
     * toute affectation enregistree n'est simplement rattachee a
     * personne — depend de la completude de l'historique d'affectations.
     */
    @Query(value = """
           SELECT va.driver_id, COUNT(ml.id)
           FROM maintenance_logs ml
           JOIN vehicle_assignments va ON va.vehicle_id = ml.vehicle_id
             AND ml.intervention_date >= va.start_date
             AND (va.end_date IS NULL OR ml.intervention_date <= va.end_date)
           WHERE ml.is_breakdown = true
             AND ml.status <> 'ANNULEE'
             AND ml.intervention_date >= :from AND ml.intervention_date <= :to
           GROUP BY va.driver_id
           """, nativeQuery = true)
    List<Object[]> countBreakdownsByDriver(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"vehicle", "garage"})
    Page<MaintenanceLog> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "garage", "items"})
    Optional<MaintenanceLog> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"vehicle", "garage"})
    List<MaintenanceLog> findByVehicleIdOrderByInterventionDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "garage"})
    List<MaintenanceLog> findByStatusOrderByInterventionDateAsc(MaintenanceStatus status);

    /**
     * Retour en atelier : meme camion, meme categorie, dans la fenetre
     * de recurrence. Signale une reparation mal faite (RG-7.6).
     */
    @Query("""
           SELECT COUNT(m) FROM MaintenanceLog m
           WHERE m.vehicle.id = :vehicleId
             AND m.category = :category
             AND m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :since
             AND (:excludeId IS NULL OR m.id <> :excludeId)
           """)
    long countRecent(@Param("vehicleId") Long vehicleId,
                     @Param("category") MaintenanceCategory category,
                     @Param("since") LocalDate since,
                     @Param("excludeId") Long excludeId);

    @Query("""
           SELECT COALESCE(SUM(m.totalCost), 0) FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           """)
    BigDecimal totalCost(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Repartition par ville d'affectation du camion, pour le bouton "Statistiques par ville". */
    @Query("""
           SELECT m.vehicle.city.id, m.vehicle.city.name, COUNT(m), COALESCE(SUM(m.totalCost), 0),
                  SUM(CASE WHEN m.isBreakdown = true THEN 1 ELSE 0 END)
           FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
             AND m.vehicle.city IS NOT NULL
           GROUP BY m.vehicle.city.id, m.vehicle.city.name
           ORDER BY SUM(m.totalCost) DESC
           """)
    List<Object[]> aggregateByCity(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Meme total, restreint aux camions affectes a l'une des villes donnees. */
    @Query("""
           SELECT COALESCE(SUM(m.totalCost), 0) FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
             AND m.vehicle.city.name IN :cityNames
           """)
    BigDecimal totalCostForCities(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                  @Param("cityNames") List<String> cityNames);

    @Query("""
           SELECT COALESCE(SUM(m.totalCost), 0) FROM MaintenanceLog m
           WHERE m.vehicle.id = :vehicleId
             AND m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           """)
    BigDecimal totalCostForVehicle(@Param("vehicleId") Long vehicleId,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Repartition par categorie, pour l'anneau de la maquette. */
    @Query("""
           SELECT m.category, COUNT(m), COALESCE(SUM(m.totalCost), 0)
           FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           GROUP BY m.category
           """)
    List<Object[]> aggregateByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Tendance quotidienne des couts, pour la courbe de la maquette. */
    @Query("""
           SELECT m.interventionDate, COALESCE(SUM(m.totalCost), 0)
           FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           GROUP BY m.interventionDate
           ORDER BY m.interventionDate
           """)
    List<Object[]> aggregateByDate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Comparatif des garages : cout moyen et taux de recurrence. */
    @Query("""
           SELECT m.garage.id, m.garage.name, COUNT(m),
                  COALESCE(SUM(m.totalCost), 0), COALESCE(AVG(m.totalCost), 0),
                  SUM(CASE WHEN m.isRecurrence = true THEN 1 ELSE 0 END)
           FROM MaintenanceLog m
           WHERE m.garage IS NOT NULL
             AND m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           GROUP BY m.garage.id, m.garage.name
           ORDER BY SUM(m.totalCost) DESC
           """)
    List<Object[]> aggregateByGarage(@Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByStatus(MaintenanceStatus status);

    /** Evite un double lavage si le planificateur tourne deux fois le meme jour. */
    boolean existsByVehicleIdAndCategoryAndInterventionDate(
            Long vehicleId, MaintenanceCategory category, LocalDate interventionDate);

    long countByIsBreakdownTrueAndInterventionDateBetween(LocalDate from, LocalDate to);

    long countByCategoryAndInterventionDateBetween(MaintenanceCategory category,
                                                   LocalDate from, LocalDate to);

    /** Interventions planifiees dans les N jours. */
    List<MaintenanceLog> findByStatusAndInterventionDateBetween(
            MaintenanceStatus status, LocalDate from, LocalDate to);

    /** Lignes brutes (camion, date, cout), pour la vue Charges par camion/ville/mois. */
    @Query("""
           SELECT m.vehicle.id, m.interventionDate, m.totalCost
           FROM MaintenanceLog m
           WHERE m.status <> com.sogeco.fleet.common.enums.MaintenanceStatus.ANNULEE
             AND m.interventionDate >= :from AND m.interventionDate <= :to
           """)
    List<Object[]> findCostRowsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
