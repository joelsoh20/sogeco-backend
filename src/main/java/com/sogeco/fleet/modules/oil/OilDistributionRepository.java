package com.sogeco.fleet.modules.oil;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** La ville d'une sortie est celle du camion servi. cityId null = toutes les villes (administrateur). */
public interface OilDistributionRepository extends JpaRepository<OilDistribution, Long> {

    @Query("""
           SELECT COALESCE(SUM(d.quantityLiters), 0) FROM OilDistribution d
           WHERE (:cityId IS NULL OR d.vehicle.city.id = :cityId)
           """)
    BigDecimal total(@Param("cityId") Long cityId);

    @Query("""
           SELECT COALESCE(SUM(d.quantityLiters), 0) FROM OilDistribution d
           WHERE d.distributionDate < :before AND (:cityId IS NULL OR d.vehicle.city.id = :cityId)
           """)
    BigDecimal totalBefore(@Param("before") LocalDate before, @Param("cityId") Long cityId);

    /** Total donne par ville du camion : ville, litres. */
    @Query("""
           SELECT d.vehicle.city.id, SUM(d.quantityLiters) FROM OilDistribution d
           WHERE d.vehicle.city IS NOT NULL GROUP BY d.vehicle.city.id
           """)
    List<Object[]> totalsByCity();

    @EntityGraph(attributePaths = {"vehicle", "vehicle.city"})
    @Query("""
           SELECT d FROM OilDistribution d WHERE (:cityId IS NULL OR d.vehicle.city.id = :cityId)
           ORDER BY d.distributionDate DESC, d.id DESC
           """)
    List<OilDistribution> recent(@Param("cityId") Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.city"})
    @Query("""
           SELECT d FROM OilDistribution d
           WHERE d.distributionDate >= :from AND d.distributionDate <= :to AND d.vehicle.city.id = :cityId
           ORDER BY d.distributionDate ASC, d.id ASC
           """)
    List<OilDistribution> inPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("cityId") Long cityId);

    @EntityGraph(attributePaths = {"vehicle", "vehicle.city"})
    Optional<OilDistribution> findWithVehicleById(Long id);
}
