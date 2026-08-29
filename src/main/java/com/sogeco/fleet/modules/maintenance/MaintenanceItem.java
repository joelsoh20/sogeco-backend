package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.MaintenanceItemType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Ligne de detail d'une intervention.
 *
 * Le cahier fonctionnel exige une liste precise des operations
 * realisees, avec couts pieces et main-d'oeuvre separes (RG-7.2).
 */
@Entity
@Table(name = "maintenance_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "maintenance_log_id", nullable = false)
    private MaintenanceLog maintenanceLog;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private MaintenanceItemType itemType;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Builder.Default
    @Column(name = "quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Colonne calculee par PostgreSQL. */
    @Column(name = "total", insertable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal total;

    /** Total en memoire, avant persistance. */
    public BigDecimal computeTotal() {
        if (quantity == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(unitPrice);
    }
}
