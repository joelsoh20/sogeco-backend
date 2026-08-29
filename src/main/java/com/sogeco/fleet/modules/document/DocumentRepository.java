package com.sogeco.fleet.modules.document;

import com.sogeco.fleet.common.enums.DocumentStatus;
import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    List<Document> findByEntityTypeAndEntityIdAndActiveTrueOrderByDocumentTypeAsc(
            EntityType entityType, Long entityId);

    Optional<Document> findFirstByEntityTypeAndEntityIdAndDocumentTypeAndActiveTrueOrderByExpiryDateDesc(
            EntityType entityType, Long entityId, DocumentType documentType);

    /** Documents portant une echeance, pour la tache quotidienne de statut. */
    List<Document> findByExpiryDateIsNotNullAndActiveTrue();

    /** Echeancier : documents arrivant a terme dans les N jours. */
    @Query("""
           SELECT d FROM Document d
           WHERE d.active = true
             AND d.expiryDate IS NOT NULL
             AND d.expiryDate <= :limit
           ORDER BY d.expiryDate ASC
           """)
    List<Document> findExpiringBefore(@Param("limit") LocalDate limit);

    long countByEntityTypeAndStatusAndActiveTrue(EntityType entityType, DocumentStatus status);

    /**
     * Documents expires d'un porteur, restreints aux types bloquants.
     * Sert au controle d'affectation (RG-4.5).
     */
    @Query("""
           SELECT d FROM Document d
           WHERE d.active = true
             AND d.entityType = :entityType
             AND d.entityId = :entityId
             AND d.documentType IN :types
             AND d.status = com.sogeco.fleet.common.enums.DocumentStatus.EXPIRE
           """)
    List<Document> findBlockingExpired(@Param("entityType") EntityType entityType,
                                       @Param("entityId") Long entityId,
                                       @Param("types") List<DocumentType> types);
}
