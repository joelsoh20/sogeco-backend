package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Carte grise — document d'immatriculation propre a chaque camion.
 *
 * Les champs administratifs (marque, chassis, carrosserie, genre, date
 * de mise en circulation) recopient ce qui figure sur le document
 * physique au moment de sa delivrance — pas une reference dynamique a
 * Vehicle, qui peut evoluer independamment.
 */
@Entity
@Table(name = "cartes_grises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarteGrise extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(name = "chassis_number", nullable = false, length = 30)
    private String chassisNumber;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    /** Genre du vehicule tel qu'imprime sur la carte grise (VP, PL, CTTE...). */
    @Column(name = "genre", length = 30)
    private String genre;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", length = 20)
    private BodyType bodyType;

    @Column(name = "seat_count")
    private Integer seatCount;

    @Column(name = "first_circulation_date")
    private LocalDate firstCirculationDate;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    @Column(name = "notes", length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    public Long daysUntilExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}
