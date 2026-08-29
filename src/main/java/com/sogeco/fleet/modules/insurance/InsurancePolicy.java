package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.InsuranceCoverageType;
import com.sogeco.fleet.common.enums.PaymentFrequency;
import com.sogeco.fleet.common.enums.PolicyStatus;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Contrat d'assurance.
 *
 * Peut couvrir plusieurs camions a la fois — un contrat flotte, courant
 * au Cameroun — via vehicles. Un seul camion est simplement le cas a
 * un seul element.
 *
 * A la creation ou au renouvellement, le document generique ASSURANCE
 * de chaque camion couvert est mis a jour avec cette echeance : le
 * blocage d'affectation (RG-4.5, deja construit au sprint 2) continue
 * de fonctionner sans modification.
 */
@Entity
@Table(name = "insurance_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InsurancePolicy extends BaseEntity {

    @Column(name = "policy_number", nullable = false, length = 50, unique = true)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner insurer;

    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", nullable = false, length = 30)
    private InsuranceCoverageType coverageType;

    @Column(name = "premium_amount", precision = 15, scale = 2)
    private BigDecimal premiumAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_frequency", nullable = false, length = 20)
    private PaymentFrequency paymentFrequency = PaymentFrequency.ANNUEL;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @Column(name = "notes", length = 500)
    private String notes;

    /** Categorie du vehicule assure (tel qu'indique sur l'attestation). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private BodyType category;

    /**
     * "Genre" saisi librement sur le formulaire — en realite l'immatriculation
     * du vehicule couvert, telle que recopiee depuis l'attestation papier. Ne
     * suppose pas que le camion existe deja dans le parc (create() essaie de le
     * relier automatiquement a vehicles s'il trouve une correspondance).
     */
    @Column(name = "vehicle_registration", length = 20)
    private String vehicleRegistration;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "policy_vehicles",
            joinColumns = @JoinColumn(name = "insurance_policy_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private Set<Vehicle> vehicles = new HashSet<>();

    // ------------------------------------------------------------------

    public Long daysRemaining() {
        return ChronoUnit.DAYS.between(LocalDate.now(), endDate);
    }

    public boolean isExpired() {
        return endDate.isBefore(LocalDate.now());
    }

    public boolean coversFleet() {
        return vehicles.size() > 1;
    }
}
