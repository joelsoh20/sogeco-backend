package com.sogeco.fleet.modules.expense;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.ExpenseCategory;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Depense hors carburant et maintenance.
 *
 * Sans cette table, la repartition des couts du tableau de bord de
 * direction serait incalculable : salaires, peages, assurances et
 * autres n'auraient nulle part ou vivre.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense extends BaseEntity {

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ExpenseCategory category;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    // ---- Imputations, toutes facultatives ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id")
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Une depense rattachee a une mission entre dans son cout direct. */
    public boolean isDirectMissionCost() {
        return mission != null && category.isMissionRelated();
    }
}
