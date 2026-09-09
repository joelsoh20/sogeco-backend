package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.partner.Partner;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Carte rose CEMAC (attestation d'assurance automobile) — document
 * propre a chaque camion, exige au meme titre que l'assurance et la
 * visite technique.
 *
 * Les champs administratifs (assure, bureau emetteur, marque et type,
 * categorie) recopient ce qui figure sur le document physique au
 * moment de sa delivrance — pas une reference dynamique a Vehicle, qui
 * peut evoluer independamment (meme logique que CarteGrise).
 */
@Entity
@Table(name = "cartes_roses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarteRose extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** Societe d'assurance : meme referentiel Partner que InsurancePolicy.insurer. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insurer_id", nullable = false)
    private Partner insurer;

    @Column(name = "insured_name", nullable = false, length = 150)
    private String insuredName;

    @Column(name = "insured_address", length = 255)
    private String insuredAddress;

    @Column(name = "issuing_bureau_name", nullable = false, length = 150)
    private String issuingBureauName;

    @Column(name = "issuing_bureau_address", length = 255)
    private String issuingBureauAddress;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    /** Marque et type du vehicule tels qu'imprimes sur la carte rose. */
    @Column(name = "vehicle_make_type", nullable = false, length = 100)
    private String vehicleMakeType;

    /** Categorie assurance du vehicule (classification CEMAC), distincte du BodyType interne. */
    @Column(name = "vehicle_category", nullable = false, length = 60)
    private String vehicleCategory;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    public Long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), validTo);
    }
}
