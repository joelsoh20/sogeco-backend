package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.RatingCriterion;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Note d'un chauffeur sur un critere, pour un mois donne.
 *
 * Une ligne par critere : le score global est leur moyenne ponderee,
 * les ponderations etant parametrables (RG-9.7).
 */
@Entity
@Table(name = "driver_ratings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverRating extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    /** Premier jour du mois evalue. */
    @Column(name = "period_month", nullable = false)
    private LocalDate periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion", nullable = false, length = 40)
    private RatingCriterion criterion;

    @Column(name = "score_100", nullable = false, precision = 5, scale = 2)
    private BigDecimal score100;

    @Builder.Default
    @Column(name = "is_automatic", nullable = false)
    private Boolean isAutomatic = Boolean.TRUE;

    @Column(name = "comment", length = 500)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_by_user_id")
    private User ratedBy;
}
