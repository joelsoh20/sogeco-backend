package com.sogeco.fleet.modules.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId, Pageable pageable);

    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to, Pageable pageable);

    /**
     * Restreint aux actions faites par un utilisateur de la ville geree
     * (RG-13.4) — AuditLog.entityId est polymorphe (vehicule, mission,
     * utilisateur...), sans relation exploitable pour filtrer sur le
     * sujet de l'action ; on filtre donc sur son auteur.
     */
    @Query("""
           SELECT a FROM AuditLog a
           WHERE a.userEmail IN (SELECT u.email FROM User u WHERE u.city.id = :cityId)
           ORDER BY a.createdAt DESC
           """)
    Page<AuditLog> findByUserCityId(@Param("cityId") Long cityId, Pageable pageable);

    @Query("""
           SELECT a FROM AuditLog a
           WHERE a.entityType = :entityType AND a.entityId = :entityId
             AND a.userEmail IN (SELECT u.email FROM User u WHERE u.city.id = :cityId)
           ORDER BY a.createdAt DESC
           """)
    Page<AuditLog> findByEntityTypeAndEntityIdAndUserCityId(
            @Param("entityType") String entityType, @Param("entityId") Long entityId,
            @Param("cityId") Long cityId, Pageable pageable);
}
