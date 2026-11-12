package com.sogeco.fleet.modules.oil;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.city.City;
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

    /** Camion de la flotte ; absent pour un vehicule hors flotte (voir externalPlate). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private Vehicle vehicle;

    /** Immatriculation d'un vehicule hors flotte (ou d'une autre ville), saisie a la main. */
    @Column(name = "external_plate", length = 30)
    private String externalPlate;

    /** Ville du fut d'ou l'huile est prise : celle du camion, ou celle choisie pour un vehicule hors flotte. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

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
