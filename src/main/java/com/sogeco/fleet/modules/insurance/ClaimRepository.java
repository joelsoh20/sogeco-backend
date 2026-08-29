package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.enums.ClaimStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    @EntityGraph(attributePaths = {"vehicle", "driver", "policy"})
    Page<Claim> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"vehicle", "driver", "policy"})
    List<Claim> findByVehicleIdOrderByIncidentDateDesc(Long vehicleId);

    /** Ce que CE chauffeur a lui-meme declare — jamais les entrees d'un autre. */
    @EntityGraph(attributePaths = {"vehicle", "driver", "policy"})
    List<Claim> findByCreatedByUserIdOrderByIncidentDateDesc(Long userId);

    long countByStatus(ClaimStatus status);

    long countByIncidentDateBetween(LocalDate from, LocalDate to);

    /** Cumul toutes periodes confondues — le compteur "Cout sinistres" de l'ecran conformite n'est pas mensuel. */
    @Query("SELECT COALESCE(SUM(c.estimatedCost), 0) FROM Claim c")
    BigDecimal totalEstimatedCost();

    /** Cumul toutes periodes confondues, meme logique que totalEstimatedCost(). */
    @Query("SELECT COALESCE(SUM(c.reimbursedAmount), 0) FROM Claim c")
    BigDecimal totalReimbursed();

    boolean existsByClaimNumber(String claimNumber);

    @Query(value = "SELECT COUNT(*) FROM claims WHERE claim_number LIKE :prefix", nativeQuery = true)
    long countByNumberPrefix(@Param("prefix") String prefix);
}
