package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    Optional<Vehicle> findByRegistrationNumberIgnoreCase(String registrationNumber);

    Optional<Vehicle> findByDeviceId(String deviceId);

    /**
     * Meme recherche, avec verrou pessimiste sur la ligne du camion --
     * serialise le traitement de deux trames du meme boitier arrivees
     * en meme temps (TelematicsIngestionService.process(), tourne en
     * @Async sur plusieurs threads). Sans ce verrou, deux transactions
     * concurrentes peuvent mettre a jour la meme alerte (ex. "Perte de
     * signal") en meme temps ; l'une des deux echoue alors au COMMIT
     * (verrou optimiste sur Alert), hors de portee d'un try/catch dans
     * la methode puisque le commit a lieu apres son retour -- toute la
     * transaction est perdue avec elle, y compris la position GPS.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehicle v WHERE v.deviceId = :deviceId")
    Optional<Vehicle> findByDeviceIdForUpdate(@Param("deviceId") String deviceId);

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

    /** Meme compteur, restreint a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    long countByActiveTrueAndCity_Id(Long cityId);

    long countByStatusAndActiveTrue(VehicleStatus status);

    /** Meme compteur, restreint a une ville — filtrage de securite d'un gestionnaire non-administrateur. */
    long countByStatusAndActiveTrueAndCity_Id(VehicleStatus status, Long cityId);

    /** Compteurs de tete d'ecran, en une seule requete. */
    @Query("""
           SELECT v.status, COUNT(v) FROM Vehicle v
           WHERE v.active = true
           GROUP BY v.status
           """)
    List<Object[]> countGroupedByStatus();

    /** Memes compteurs, restreints a une ville. */
    @Query("""
           SELECT v.status, COUNT(v) FROM Vehicle v
           WHERE v.active = true AND v.city.id = :cityId
           GROUP BY v.status
           """)
    List<Object[]> countGroupedByStatusForCity(@Param("cityId") Long cityId);

    /** Camions dont le seuil de maintenance preventive approche. */
    @Query("""
           SELECT v FROM Vehicle v
           WHERE v.active = true
             AND ((v.nextMaintenanceKm IS NOT NULL AND v.currentKilometers >= v.nextMaintenanceKm - :kmWarning)
               OR (v.nextMaintenanceDate IS NOT NULL AND v.nextMaintenanceDate <= :dateLimit))
           """)
    List<Vehicle> findDueForMaintenance(java.math.BigDecimal kmWarning, LocalDate dateLimit);

    /**
     * Consommation moyenne connue des camions de meme carrosserie, pour
     * estimer le niveau de reservoir d'un camion qui n'a pas encore
     * assez d'historique pour son propre taux (FuelAnalyticsService.
     * tankLevelFor). Null si aucun camion de cette carrosserie n'a
     * encore de taux calcule.
     */
    @Query("""
           SELECT AVG(v.avgFuelConsumption) FROM Vehicle v
           WHERE v.bodyType = :bodyType AND v.avgFuelConsumption IS NOT NULL
           """)
    java.math.BigDecimal averageFuelConsumptionForBodyType(@Param("bodyType") BodyType bodyType);

    /**
     * Recherche pour la barre de recherche du tableau de bord —
     * immatriculation, marque ou modele. cityId null = pas de filtre
     * (administrateur, qui voit toute la flotte).
     */
    @EntityGraph(attributePaths = "city")
    @Query("""
           SELECT v FROM Vehicle v
           WHERE v.active = true
             AND (:cityId IS NULL OR v.city.id = :cityId)
             AND (LOWER(v.registrationNumber) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(v.brand) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(v.model) LIKE LOWER(CONCAT('%', :q, '%')))
           ORDER BY v.registrationNumber ASC
           """)
    List<Vehicle> search(@Param("q") String q, @Param("cityId") Long cityId, Pageable pageable);
}
