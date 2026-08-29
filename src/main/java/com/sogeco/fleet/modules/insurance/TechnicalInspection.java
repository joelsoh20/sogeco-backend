package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.InspectionResult;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Visite technique.
 *
 * Le resultat est ce que le document generique ne capture pas : lui ne
 * connait qu'une date d'expiration, pas l'issue du controle. Une
 * non-conformite immobilise le camion (RG-8.5), au meme titre qu'une
 * assurance expiree.
 */
@Entity
@Table(name = "technical_inspections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalInspection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner center;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "next_inspection_date", nullable = false)
    private LocalDate nextInspectionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 30)
    private InspectionResult result;

    @Column(name = "defects_noted", length = 500)
    private String defectsNoted;

    @Builder.Default
    @Column(name = "cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal cost = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    public Long daysUntilNext() {
        return ChronoUnit.DAYS.between(LocalDate.now(), nextInspectionDate);
    }
}
