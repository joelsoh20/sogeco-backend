package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.modules.document.Document;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Licence de transport — autorisation qui couvre l'ensemble de la
 * flotte, jamais un camion en particulier (a la difference de la
 * carte bleue). D'ou l'absence de tout lien vers Vehicle ici.
 */
@Entity
@Table(name = "transport_licenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransportLicense extends BaseEntity {

    @Column(name = "reference", nullable = false, length = 50, unique = true)
    private String reference;

    @Column(name = "issuing_authority", length = 150)
    private String issuingAuthority;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    /** Puissance fiscale du vehicule couvert — texte libre (chiffres et/ou lettres selon la notation de la licence). */
    @Column(name = "power", length = 30)
    private String power;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    public Long daysRemaining() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    public boolean isExpired() {
        return expiryDate.isBefore(LocalDate.now());
    }
}
