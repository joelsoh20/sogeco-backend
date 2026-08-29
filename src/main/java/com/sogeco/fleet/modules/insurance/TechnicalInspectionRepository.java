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

    /** Cout total des visites techniques passees sur la periode, pour la repartition des couts. */
    @Query("""
           SELECT COALESCE(SUM(i.cost), 0) FROM TechnicalInspection i
           WHERE i.inspectionDate >= :from AND i.inspectionDate <= :to
           """)
    BigDecimal totalCost(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
