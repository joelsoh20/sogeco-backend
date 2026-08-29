package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Carte bleue — document de circulation propre a chaque camion au
 * Cameroun, au meme titre que la visite technique (un camion, une
 * carte). A la difference de la licence de transport, qui couvre
 * toute la flotte.
 */
@Entity
@Table(name = "cartes_bleues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarteBleue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "receipt_number", nullable = false, length = 50, unique = true)
    private String receiptNumber;

    /** Categorie de transport (ex. "S6-Marchandise compte propre"). */
    @Column(name = "category", length = 150)
    private String category;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    /** Puissance fiscale du vehicule couvert, en CV. */
    @Column(name = "power", precision = 6, scale = 2)
    private BigDecimal power;

    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    public Long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}
