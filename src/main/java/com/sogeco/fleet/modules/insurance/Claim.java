package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.ClaimStatus;
import com.sogeco.fleet.common.enums.ClaimType;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.maintenance.MaintenanceLog;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sinistre.
 *
 * Le rattachement a une police et a une intervention de maintenance
 * est facultatif : un accrochage mineur peut n'etre jamais declare a
 * l'assureur, et toutes les reparations ne decoulent pas d'un sinistre
 * declare.
 */
@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim extends BaseEntity {

    @Column(name = "claim_number", nullable = false, length = 50, unique = true)
    private String claimNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurance_policy_id")
    private InsurancePolicy policy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_log_id")
    private MaintenanceLog maintenance;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    @Column(name = "police_report_number", length = 60)
    private String policeReportNumber;

    @Column(name = "estimated_cost", precision = 15, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "deductible_amount", precision = 15, scale = 2)
    private BigDecimal deductibleAmount;

    @Column(name = "reimbursed_amount", precision = 15, scale = 2)
    private BigDecimal reimbursedAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ClaimStatus status = ClaimStatus.DECLARE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    /** Part du cout estime reellement a la charge de SOGECO. */
    public BigDecimal netCost() {
        if (estimatedCost == null) {
            return null;
        }
        BigDecimal reimbursed = reimbursedAmount == null ? BigDecimal.ZERO : reimbursedAmount;
        return estimatedCost.subtract(reimbursed);
    }
}
