package com.sogeco.fleet.modules.client;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.PricingMode;
import com.sogeco.fleet.modules.route.Route;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne de la grille tarifaire.
 *
 * Un client nul designe un tarif general, un corridor nul un tarif
 * valable quel que soit le trajet. La resolution retient toujours le
 * tarif le plus specifique disponible (voir TariffService).
 */
@Entity
@Table(name = "tariffs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tariff extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_mode", nullable = false, length = 20)
    private PricingMode pricingMode;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Plancher applique si le calcul donne un montant inferieur. */
    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    public boolean isValidOn(LocalDate date) {
        return Boolean.TRUE.equals(getActive())
                && !date.isBefore(validFrom)
                && (validTo == null || !date.isAfter(validTo));
    }

    /** Plus le tarif est cible, plus il prime dans la resolution. */
    public int specificity() {
        int score = 0;
        if (client != null) score += 2;
        if (route != null)  score += 1;
        return score;
    }

    public BigDecimal compute(BigDecimal distanceKm, BigDecimal weightKg) {
        BigDecimal amount = pricingMode.apply(unitPrice, distanceKm, weightKg);
        if (minAmount != null && amount.compareTo(minAmount) < 0) {
            return minAmount;
        }
        return amount;
    }
}
