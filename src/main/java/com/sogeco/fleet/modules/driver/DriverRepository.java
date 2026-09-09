package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.enums.DriverStatus;
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

public interface DriverRepository extends JpaRepository<Driver, Long>, JpaSpecificationExecutor<Driver> {

    Optional<Driver> findByMatriculeIgnoreCase(String matricule);

    Optional<Driver> findByUserId(Long userId);

    boolean existsByMatriculeIgnoreCase(String matricule);

    @EntityGraph(attributePaths = "city")
    Page<Driver> findAllBy(Pageable pageable);

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    @EntityGraph(attributePaths = "city")
    Page<Driver> findAllByCity_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = "city")
    List<Driver> findByActiveTrueOrderByLastNameAsc();

    List<Driver> findByStatusAndActiveTrue(DriverStatus status);

    long countByStatusAndActiveTrue(DriverStatus status);

    long countByActiveTrue();

    /** Permis arrivant a echeance, pour les alertes J-60, J-30, J-7. */
    List<Driver> findByActiveTrueAndLicenseExpiryDateLessThanEqual(LocalDate limit);

    /** Classement de l'ecran Chauffeurs, du meilleur score au moins bon. */
    @EntityGraph(attributePaths = "city")
    List<Driver> findByActiveTrueAndPerformanceScoreIsNotNullOrderByPerformanceScoreDesc();

    /** Meme classement, restreint a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    @EntityGraph(attributePaths = "city")
    List<Driver> findByActiveTrueAndPerformanceScoreIsNotNullAndCity_IdOrderByPerformanceScoreDesc(Long cityId);

    @Query("SELECT AVG(d.performanceScore) FROM Driver d WHERE d.active = true AND d.performanceScore IS NOT NULL")
    BigDecimal averagePerformanceScore();

    @Query("SELECT COALESCE(SUM(d.incidentsCount), 0) FROM Driver d WHERE d.active = true")
    Long totalIncidents();

    @Query("SELECT COALESCE(SUM(d.totalKilometers), 0) FROM Driver d WHERE d.active = true")
    BigDecimal totalKilometers();

    @Query("""
           SELECT d FROM Driver d
           WHERE d.active = true
             AND d.status = com.sogeco.fleet.common.enums.DriverStatus.ACTIF
             AND NOT EXISTS (
                 SELECT 1 FROM VehicleAssignment a
                 WHERE a.driver = d AND a.endDate IS NULL)
           ORDER BY d.lastName ASC
           """)
    List<Driver> findUnassigned();

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    @Query("""
           SELECT d FROM Driver d
           WHERE d.active = true
             AND d.status = com.sogeco.fleet.common.enums.DriverStatus.ACTIF
             AND d.city.id = :cityId
             AND NOT EXISTS (
                 SELECT 1 FROM VehicleAssignment a
                 WHERE a.driver = d AND a.endDate IS NULL)
           ORDER BY d.lastName ASC
           """)
    List<Driver> findUnassignedForCity(@Param("cityId") Long cityId);

    @Query("""
           SELECT d FROM Driver d
           WHERE d.active = true AND d.city.id = :cityId
           ORDER BY d.lastName ASC
           """)
    List<Driver> findByCity(@Param("cityId") Long cityId);

    /**
     * Recherche pour la barre de recherche du tableau de bord — nom,
     * prenom ou matricule. cityId null = pas de filtre (administrateur).
     */
    @EntityGraph(attributePaths = "city")
    @Query("""
           SELECT d FROM Driver d
           WHERE d.active = true
             AND (:cityId IS NULL OR d.city.id = :cityId)
             AND (LOWER(d.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(d.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(d.matricule) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY d.lastName ASC
           """)
    List<Driver> search(@Param("q") String q, @Param("cityId") Long cityId, Pageable pageable);
}
