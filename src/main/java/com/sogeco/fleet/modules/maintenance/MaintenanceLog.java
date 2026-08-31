package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.MaintenanceCategory;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Intervention de maintenance.
 *
 * isBreakdown distingue une panne subie d'un entretien planifie : c'est
 * la base du ratio preventif / curatif, indicateur de maturite du
 * module. Une flotte bien geree tend vers 70 % de preventif.
 */
@Entity
@Table(name = "maintenance_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner garage;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private MaintenanceCategory category;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "intervention_date", nullable = false)
    private LocalDate interventionDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "odometer_km", precision = 12, scale = 2)
    private BigDecimal odometerKm;

    /** Carburant consomme pour les essais du vehicule pendant le passage au garage. */
    @Column(name = "test_fuel_liters", precision = 8, scale = 2)
    private BigDecimal testFuelLiters;

    @Builder.Default
    @Column(name = "parts_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal partsCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "labor_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal laborCost = BigDecimal.ZERO;

    /** Colonne calculee par PostgreSQL. */
    @Column(name = "total_cost", insertable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MaintenanceStatus status = MaintenanceStatus.PLANIFIEE;

    @Builder.Default
    @Column(name = "downtime_days", nullable = false)
    private Integer downtimeDays = 0;

    @Builder.Default
    @Column(name = "is_breakdown", nullable = false)
    private Boolean isBreakdown = Boolean.FALSE;

    /** Retour en atelier : intervention identique sous 30 jours (RG-7.6). */
    @Builder.Default
    @Column(name = "is_recurrence", nullable = false)
    private Boolean isRecurrence = Boolean.FALSE;

    @Column(name = "error_code", length = 30)
    private String errorCode;

    @Column(name = "next_intervention_date")
    private LocalDate nextInterventionDate;

    @Column(name = "next_intervention_km", precision = 12, scale = 2)
    private BigDecimal nextInterventionKm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_document_id")
    private Document invoice;

    @Builder.Default
    @OneToMany(mappedBy = "maintenanceLog", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaintenanceItem> items = new ArrayList<>();

    // ------------------------------------------------------------------

    public void addItem(MaintenanceItem item) {
        items.add(item);
        item.setMaintenanceLog(this);
    }

    /** Recalcule les couts a partir des lignes de detail. */
    public void recomputeCosts() {
        this.partsCost = items.stream()
                .filter(item -> item.getItemType() == com.sogeco.fleet.common.enums.MaintenanceItemType.PIECE)
                .map(MaintenanceItem::computeTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        this.laborCost = items.stream()
                .filter(item -> item.getItemType() == com.sogeco.fleet.common.enums.MaintenanceItemType.MAIN_OEUVRE)
                .map(MaintenanceItem::computeTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void complete(LocalDate date) {
        this.status = MaintenanceStatus.TERMINEE;
        this.completionDate = date == null ? LocalDate.now() : date;
        if (interventionDate != null) {
            this.downtimeDays = (int) java.time.temporal.ChronoUnit.DAYS
                    .between(interventionDate, this.completionDate);
        }
    }

    /** Delai avant la prochaine intervention, en jours. */
    public Long daysUntilNext() {
        if (nextInterventionDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), nextInterventionDate);
    }
}
