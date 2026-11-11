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

/** cityId null = toutes les villes (administrateur). */
public interface OilPurchaseRepository extends JpaRepository<OilPurchase, Long> {

    @Query("SELECT COALESCE(SUM(p.quantityLiters), 0) FROM OilPurchase p WHERE (:cityId IS NULL OR p.city.id = :cityId)")
    BigDecimal total(@Param("cityId") Long cityId);

    @Query("""
           SELECT COALESCE(SUM(p.quantityLiters), 0) FROM OilPurchase p
           WHERE p.purchaseDate < :before AND (:cityId IS NULL OR p.city.id = :cityId)
           """)
    BigDecimal totalBefore(@Param("before") LocalDate before, @Param("cityId") Long cityId);

    /** Total achete par ville : ville, nom, litres. */
    @Query("SELECT p.city.id, p.city.name, SUM(p.quantityLiters) FROM OilPurchase p GROUP BY p.city.id, p.city.name")
    List<Object[]> totalsByCity();

    @EntityGraph(attributePaths = "city")
    @Query("""
           SELECT p FROM OilPurchase p WHERE (:cityId IS NULL OR p.city.id = :cityId)
           ORDER BY p.purchaseDate DESC, p.id DESC
           """)
    List<OilPurchase> recent(@Param("cityId") Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = "city")
    @Query("""
           SELECT p FROM OilPurchase p
           WHERE p.purchaseDate >= :from AND p.purchaseDate <= :to AND p.city.id = :cityId
           ORDER BY p.purchaseDate ASC, p.id ASC
           """)
    List<OilPurchase> inPeriod(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("cityId") Long cityId);

    @EntityGraph(attributePaths = "city")
    Optional<OilPurchase> findWithCityById(Long id);
}
