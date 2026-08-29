package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.PolicyStatus;
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

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    Page<InsurancePolicy> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    Optional<InsurancePolicy> findWithVehiclesById(Long id);

    @Query("""
           SELECT p FROM InsurancePolicy p JOIN p.vehicles v
           WHERE v.id = :vehicleId AND p.status = com.sogeco.fleet.common.enums.PolicyStatus.ACTIVE
           ORDER BY p.endDate DESC
           """)
    Optional<InsurancePolicy> findActiveForVehicle(@Param("vehicleId") Long vehicleId);

    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    List<InsurancePolicy> findByStatusAndEndDateLessThanEqual(PolicyStatus status, LocalDate limit);

    List<InsurancePolicy> findByStatus(PolicyStatus status);

    long countByStatus(PolicyStatus status);

    boolean existsByPolicyNumber(String policyNumber);

    /** Prime totale des polices souscrites/renouvelees sur la periode, pour la repartition des couts. */
    @Query("""
           SELECT COALESCE(SUM(p.premiumAmount), 0) FROM InsurancePolicy p
           WHERE p.startDate >= :from AND p.startDate <= :to
           """)
    BigDecimal totalPremiumCost(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
