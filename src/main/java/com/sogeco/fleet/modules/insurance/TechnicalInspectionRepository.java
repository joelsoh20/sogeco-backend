package com.sogeco.fleet.modules.insurance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TechnicalInspectionRepository extends JpaRepository<TechnicalInspection, Long> {

    @EntityGraph(attributePaths = {"vehicle", "center"})
    Page<TechnicalInspection> findAllBy(Pageable pageable);

    /** Meme liste, restreinte a une ville — filtrage de securite d'un gestionnaire non-administrateur (RG-13.4). */
    @EntityGraph(attributePaths = {"vehicle", "center"})
    Page<TechnicalInspection> findAllByVehicle_City_Id(Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "center"})
    List<TechnicalInspection> findByVehicleIdOrderByInspectionDateDesc(Long vehicleId);

    /** Ce que CE chauffeur a lui-meme saisi — jamais les entrees d'un autre. */
    @EntityGraph(attributePaths = {"vehicle", "center"})
    List<TechnicalInspection> findByCreatedByUserIdOrderByInspectionDateDesc(Long userId);

    @Query("""
           SELECT i FROM TechnicalInspection i
           WHERE i.vehicle.id = :vehicleId
           ORDER BY i.inspectionDate DESC
           LIMIT 1
           """)
    Optional<TechnicalInspection> findLatestForVehicle(@Param("vehicleId") Long vehicleId);

    @EntityGraph(attributePaths = {"vehicle", "center"})
    List<TechnicalInspection> findByNextInspectionDateLessThanEqual(LocalDate limit);

    long countByResultNot(com.sogeco.fleet.common.enums.InspectionResult result);

    /** Vrai si ce camion a deja au moins une visite, quelle que soit son echeance. */
    boolean existsByVehicleId(Long vehicleId);

    /** Cout total des visites techniques passees sur la periode, pour la repartition des couts. */
    @Query("""
           SELECT COALESCE(SUM(i.cost), 0) FROM TechnicalInspection i
           WHERE i.inspectionDate >= :from AND i.inspectionDate <= :to
           """)
    BigDecimal totalCost(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Meme total, restreint a une ville — filtrage de securite d'un gestionnaire non-administrateur (RG-13.4). cityId null = pas de filtre. */
    @Query("""
           SELECT COALESCE(SUM(i.cost), 0) FROM TechnicalInspection i
           WHERE i.inspectionDate >= :from AND i.inspectionDate <= :to
             AND (:cityId IS NULL OR i.vehicle.city.id = :cityId)
           """)
    BigDecimal totalCostForCity(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                @Param("cityId") Long cityId);
}
