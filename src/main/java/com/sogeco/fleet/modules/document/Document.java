package com.sogeco.fleet.modules.document;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.DocumentStatus;
import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Document rattache a n'importe quelle entite.
 *
 * Table polymorphe : entityType + entityId designent le porteur, sans
 * cle etrangere. C'est le compromis assume pour ne pas multiplier les
 * tables de pieces jointes.
 *
 * Les documents porteurs d'une echeance alimentent le bloc
 * "Documents & Echeances" de la fiche camion.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 40)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "reference_number", length = 60)
    private String referenceNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.SANS_ECHEANCE;

    @Column(name = "notes", length = 255)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_user_id")
    private User uploadedBy;

    // ------------------------------------------------------------------

    /**
     * Jours restants avant expiration. Valeur negative si le document
     * est deja expire, comme l'affiche l'ecran Assurance (RG-8.3).
     */
    public Long daysRemaining() {
        if (expiryDate == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Recalcule le statut. Appele a la creation et par la tache
     * quotidienne de controle des echeances (RG-8.2).
     */
    public void refreshStatus(int warningDays) {
        if (expiryDate == null) {
            this.status = DocumentStatus.SANS_ECHEANCE;
            return;
        }
        long remaining = daysRemaining();
        if (remaining < 0) {
            this.status = DocumentStatus.EXPIRE;
        } else if (remaining <= warningDays) {
            this.status = DocumentStatus.A_RENOUVELER;
        } else {
            this.status = DocumentStatus.VALIDE;
        }
    }

    public boolean isExpired() {
        return status == DocumentStatus.EXPIRE;
    }
}
