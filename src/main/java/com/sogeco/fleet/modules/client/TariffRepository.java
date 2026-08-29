package com.sogeco.fleet.modules.client;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TariffRepository extends JpaRepository<Tariff, Long> {

    @EntityGraph(attributePaths = {"client", "serviceType", "route"})
    List<Tariff> findByActiveTrueOrderByValidFromDesc();

    @EntityGraph(attributePaths = {"client", "serviceType", "route"})
    List<Tariff> findByClientIdAndActiveTrue(Long clientId);

    /**
     * Tarifs candidats pour une prestation donnee : ceux du client et
     * les tarifs generaux, sur le corridor concerne ou sans corridor.
     * Le choix du plus specifique est fait en Java.
     */
    @Query("""
           SELECT t FROM Tariff t
           WHERE t.active = true
             AND t.serviceType.id = :serviceTypeId
             AND (t.client IS NULL OR t.client.id = :clientId)
             AND (t.route IS NULL OR t.route.id = :routeId)
             AND t.validFrom <= :date
             AND (t.validTo IS NULL OR t.validTo >= :date)
           """)
    List<Tariff> findCandidates(@Param("clientId") Long clientId,
                                @Param("serviceTypeId") Long serviceTypeId,
                                @Param("routeId") Long routeId,
                                @Param("date") LocalDate date);
}
