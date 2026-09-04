package com.sogeco.fleet.modules.fuel;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.FuelLogStatus;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Ravitaillement.
 *
 * La consommation se calcule pour chaque plein, complet ou partiel,
 * a partir des litres ravitailles et de la distance parcourue depuis
 * le releve precedent. Cette distance est etablie a partir du suivi
 * GPS (module tracking) ; l'ecart d'odometre saisi manuellement ne
 * sert plus qu'en repli, quand aucune donnee GPS n'est disponible
 * (camion sans boitier, coupure de signal).
 */
@Entity
@Table(name = "fuel_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FuelLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    /** Rattachement automatique a la mission en cours (RG-6.9). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner station;

    @Column(name = "fuel_datetime", nullable = false)
    private Instant fuelDatetime;

    @Column(name = "quantity_liters", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantityLiters;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "odometer_before", precision = 12, scale = 2)
    private BigDecimal odometerBefore;

    @Column(name = "odometer_after", nullable = false, precision = 12, scale = 2)
    private BigDecimal odometerAfter;

    @Builder.Default
    @Column(name = "full_tank", nullable = false)
    private Boolean fullTank = Boolean.TRUE;

    @Column(name = "receipt_number", length = 50)
    private String receiptNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_document_id")
    private Document receipt;

    @Column(name = "computed_consumption", precision = 6, scale = 2)
    private BigDecimal computedConsumption;

    /**
     * Distance retenue (GPS, ou odometre a defaut) pour ce plein -- la
     * meme valeur qui a servi a calculer computedConsumption ci-dessus.
     * Persistee pour permettre une moyenne par somme (litres/km) au
     * niveau du camion, cf. FuelService.average().
     */
    @Column(name = "distance_km", precision = 10, scale = 3)
    private BigDecimal distanceKm;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FuelLogStatus status = FuelLogStatus.VALIDE;

    @Column(name = "anomaly_reason", length = 255)
    private String anomalyReason;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    // ------------------------------------------------------------------

    /** Ecart d'odometre depuis le releve precedent, a titre indicatif. */
    public BigDecimal distanceCovered() {
        if (odometerBefore == null || odometerAfter == null) {
            return null;
        }
        return odometerAfter.subtract(odometerBefore);
    }

    /**
     * Consommation en L/100 km : litres x 100 / distance.
     * La distance est fournie par l'appelant (FuelService), etablie a
     * partir du suivi GPS ou, a defaut, de l'ecart d'odometre. Nulle
     * si cette distance est inconnue ou nulle.
     */
    public BigDecimal computeConsumption(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() <= 0) {
            return null;
        }
        return quantityLiters.multiply(BigDecimal.valueOf(100))
                .divide(distanceKm, 2, RoundingMode.HALF_UP);
    }

    public void markAnomaly(String reason) {
        this.status = FuelLogStatus.ANOMALIE;
        this.anomalyReason = reason;
    }

    public boolean isCounted() {
        return status != FuelLogStatus.ANNULE;
    }
}
