package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.BonusStatus;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Prime mensuelle de performance.
 *
 * Le montant est propose automatiquement par bareme selon le score,
 * puis validable ou modifiable avec justification (RG-9.11).
 */
@Entity
@Table(name = "driver_bonuses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverBonus extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "performance_score", precision = 5, scale = 2)
    private BigDecimal performanceScore;

    @Column(name = "reason", length = 255)
    private String reason;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BonusStatus status = BonusStatus.PROPOSEE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedBy;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public boolean isEditable() {
        return status == BonusStatus.PROPOSEE || status == BonusStatus.VALIDEE;
    }
}
