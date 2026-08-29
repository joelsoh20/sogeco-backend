package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByRegistrationNumberIgnoreCase(String registrationNumber);

    Optional<Vehicle> findByDeviceId(String deviceId);

    boolean existsByRegistrationNumberIgnoreCase(String registrationNumber);

    boolean existsByVinNumberIgnoreCase(String vinNumber);

    @EntityGraph(attributePaths = "city")
    Page<Vehicle> findAllBy(Pageable pageable);

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    @EntityGraph(attributePaths = "city")
    Page<Vehicle> findAllByCity_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = "city")
    List<Vehicle> findByActiveTrueOrderByRegistrationNumberAsc();

    /** Memes camions actifs, restreints a une ville (cityId null = pas de filtre) — ecran Carburant. */
    @Query("""
           SELECT v FROM Vehicle v
           WHERE v.active = true
             AND (:cityId IS NULL OR v.city.id = :cityId)
           ORDER BY v.registrationNumber ASC
           """)
    List<Vehicle> findActiveForCity(@Param("cityId") Long cityId);

    List<Vehicle> findByStatusAndActiveTrue(VehicleStatus status);

    /** Camions en tour de ville, pour le lavage automatique du samedi (LaverieScheduler). */
    List<Vehicle> findByActiveTrueAndUsageType(UsageType usageType);

    long countByActiveTrue();

    long countByStatusAndActiveTrue(VehicleStatus status);

    /** Compteurs de tete d'ecran, en une seule requete. */
    @Query("""
           SELECT v.status, COUNT(v) FROM Vehicle v
           WHERE v.active = true
           GROUP BY v.status
           """)
    List<Object[]> countGroupedByStatus();

    /** Camions dont le seuil de maintenance preventive approche. */
    @Query("""
           SELECT v FROM Vehicle v
           WHERE v.active = true
             AND ((v.nextMaintenanceKm IS NOT NULL AND v.currentKilometers >= v.nextMaintenanceKm - :kmWarning)
               OR (v.nextMaintenanceDate IS NOT NULL AND v.nextMaintenanceDate <= :dateLimit))
           """)
    List<Vehicle> findDueForMaintenance(java.math.BigDecimal kmWarning, LocalDate dateLimit);

    /** Recherche pour la barre de recherche du tableau de bord — immatriculation, marque ou modele. */
    @EntityGraph(attributePaths = "city")
    @Query("""
           SELECT v FROM Vehicle v
           WHERE v.active = true
             AND (LOWER(v.registrationNumber) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(v.model) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY v.registrationNumber ASC
           """)
    List<Vehicle> search(@Param("q") String q, Pageable pageable);
}
