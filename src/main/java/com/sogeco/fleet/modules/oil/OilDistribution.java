package com.sogeco.fleet.modules.oil;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Huile moteur donnee a un camion (sortie du fut). */
@Entity
@Table(name = "oil_distributions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OilDistribution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "distribution_date", nullable = false)
    private LocalDate distributionDate;

    @Column(name = "quantity_liters", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantityLiters;

    /** Chauffeur ou personne ayant recu l'huile. */
    @Column(name = "recipient_name", length = 150)
    private String recipientName;

    @Column(name = "note", length = 200)
    private String note;
}
