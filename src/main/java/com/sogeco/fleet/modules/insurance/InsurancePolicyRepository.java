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

    /**
     * Memes polices, restreintes a celles couvrant au moins un camion de
     * la ville geree (RG-13.4) — une police peut couvrir plusieurs
     * camions de villes differentes (ManyToMany), d'ou le DISTINCT et
     * le countQuery explicite (sinon Spring Data compterait une police
     * en double si plusieurs de ses camions sont dans la meme ville).
     */
    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    @Query(value = """
           SELECT DISTINCT p FROM InsurancePolicy p JOIN p.vehicles v
           WHERE v.city.id = :cityId
           """,
           countQuery = """
           SELECT COUNT(DISTINCT p) FROM InsurancePolicy p JOIN p.vehicles v
           WHERE v.city.id = :cityId
           """)
    Page<InsurancePolicy> findAllByVehicleCityId(@Param("cityId") Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    Optional<InsurancePolicy> findWithVehiclesById(Long id);

    @Query("""
           SELECT p FROM InsurancePolicy p JOIN p.vehicles v
           WHERE v.id = :vehicleId AND p.status = com.sogeco.fleet.common.enums.PolicyStatus.ACTIVE
           ORDER BY p.endDate DESC
           """)
    Optional<InsurancePolicy> findActiveForVehicle(@Param("vehicleId") Long vehicleId);

    /** Vrai si ce camion a deja au moins une police, expiree ou non — pour signaler l'absence totale, pas juste l'echeance. */
    boolean existsByVehicles_Id(Long vehicleId);

    @EntityGraph(attributePaths = {"insurer", "vehicles"})
    List<InsurancePolicy> findByStatusAndEndDateLessThanEqual(PolicyStatus status, LocalDate limit);

    List<InsurancePolicy> findByStatus(PolicyStatus status);

    long countByStatus(PolicyStatus status);

    boolean existsByPolicyNumber(String policyNumber);

    boolean existsByPolicyNumberAndIdNot(String policyNumber, Long id);

    /** Prime totale des polices souscrites/renouvelees sur la periode, pour la repartition des couts. */
    @Query("""
           SELECT COALESCE(SUM(p.premiumAmount), 0) FROM InsurancePolicy p
           WHERE p.startDate >= :from AND p.startDate <= :to
           """)
    BigDecimal totalPremiumCost(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Meme total, restreint aux polices couvrant au moins un camion de la
     * ville geree — filtrage de securite d'un gestionnaire non-administrateur
     * (RG-13.4). cityId null = pas de filtre. EXISTS plutot qu'un JOIN : une
     * police couvrant plusieurs camions de la meme ville ne doit compter
     * qu'une fois, jamais multiplier sa prime par camion couvert.
     */
    @Query("""
           SELECT COALESCE(SUM(p.premiumAmount), 0) FROM InsurancePolicy p
           WHERE p.startDate >= :from AND p.startDate <= :to
             AND (:cityId IS NULL OR EXISTS (
                 SELECT 1 FROM p.vehicles v WHERE v.city.id = :cityId))
           """)
    BigDecimal totalPremiumCostForCity(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                       @Param("cityId") Long cityId);
}
