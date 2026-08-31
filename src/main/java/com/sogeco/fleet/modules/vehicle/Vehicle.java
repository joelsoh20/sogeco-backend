package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.UsageType;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.document.Document;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Camion de la flotte.
 *
 * Les compteurs (kilometrage, niveau de carburant, consommation moyenne)
 * sont des valeurs denormalisees alimentees par la telematique et les
 * pleins. Ce sont des caches, jamais la source de verite.
 */
@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vehicle extends SoftDeletableEntity {

    // ---- Identite administrative ----

    @Column(name = "registration_number", nullable = false, length = 20, unique = true)
    private String registrationNumber;

    @Column(name = "vin_number", length = 30, unique = true)
    private String vinNumber;

    @Column(name = "brand", nullable = false, length = 60)
    private String brand;

    @Column(name = "model", nullable = false, length = 60)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "body_type", nullable = false, length = 20)
    private BodyType bodyType;

    @Column(name = "capacity_tons", precision = 6, scale = 2)
    private BigDecimal capacityTons;

    @Column(name = "tank_capacity_liters", precision = 8, scale = 2)
    private BigDecimal tankCapacityLiters;

    @Column(name = "gross_weight_kg", precision = 10, scale = 2)
    private BigDecimal grossWeightKg;

    @Column(name = "first_registration_date")
    private LocalDate firstRegistrationDate;

    @Column(name = "owner_name", length = 120)
    private String ownerName;

    // ---- Acquisition ----

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    // ---- Exploitation ----

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VehicleStatus status = VehicleStatus.DISPONIBLE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    /** Voyage (longue distance) ou tour de ville (rotations locales). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 20)
    private UsageType usageType = UsageType.VOYAGE;

    /**
     * Frais de lavage hebdomadaire, uniquement pour un camion en tour de
     * ville — deduit automatiquement chaque samedi (LaverieScheduler).
     * Deux montants possibles seulement : 2500 ou 3000 FCFA.
     */
    @Column(name = "weekly_wash_cost", precision = 15, scale = 2)
    private BigDecimal weeklyWashCost;

    /** Identifiant du boitier telematique (sprint 5). */
    @Column(name = "device_id", length = 60, unique = true)
    private String deviceId;

    // ---- Compteurs denormalises ----

    @Builder.Default
    @Column(name = "current_kilometers", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentKilometers = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "daily_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyKm = BigDecimal.ZERO;

    @Column(name = "fuel_level_percent", precision = 5, scale = 2)
    private BigDecimal fuelLevelPercent;

    @Column(name = "fuel_level_liters", precision = 8, scale = 2)
    private BigDecimal fuelLevelLiters;

    @Column(name = "avg_fuel_consumption", precision = 6, scale = 2)
    private BigDecimal avgFuelConsumption;

    /**
     * Moyenne des pleins rattaches a une mission chargee (tonnage renseigne,
     * au moins {@code fuel.loaded_threshold_percent} de la capacite du
     * camion) -- distincte de {@link #avgFuelConsumption}, qui reste la
     * moyenne generale (tous pleins confondus) utilisee des qu'aucune
     * information de tonnage n'est disponible. Nulle tant qu'aucun plein
     * charge n'a ete releve : le tonnage restant facultatif, cette valeur
     * peut legitimement ne jamais exister pour un camion donne.
     */
    @Column(name = "avg_fuel_consumption_loaded", precision = 6, scale = 2)
    private BigDecimal avgFuelConsumptionLoaded;

    // ---- Maintenance preventive ----

    @Column(name = "next_maintenance_date")
    private LocalDate nextMaintenanceDate;

    @Column(name = "next_maintenance_km", precision = 12, scale = 2)
    private BigDecimal nextMaintenanceKm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_document_id")
    private Document photo;

    // ------------------------------------------------------------------
    // Comportement
    // ------------------------------------------------------------------

    public String getDisplayName() {
        return "%s %s (%s)".formatted(brand, model, registrationNumber);
    }

    /**
     * Le kilometrage ne peut que croitre (RG-4.6). Une correction a la
     * baisse passe par un administrateur et une methode dediee.
     */
    public void updateKilometers(BigDecimal newValue) {
        if (newValue == null) {
            return;
        }
        if (currentKilometers != null && newValue.compareTo(currentKilometers) < 0) {
            throw new IllegalArgumentException(
                    "Le kilometrage ne peut pas diminuer : %s -> %s".formatted(currentKilometers, newValue));
        }
        this.currentKilometers = newValue;
    }

    /** Correction administrative, tracee dans le journal d'audit. */
    public void forceKilometers(BigDecimal newValue) {
        this.currentKilometers = newValue;
    }

    public void addDistance(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.signum() <= 0) {
            return;
        }
        this.currentKilometers = this.currentKilometers.add(distanceKm);
        this.dailyKm = this.dailyKm.add(distanceKm);
    }

    public void resetDailyKm() {
        this.dailyKm = BigDecimal.ZERO;
    }

    public boolean isAssignable() {
        return Boolean.TRUE.equals(getActive()) && status.isAssignable();
    }

    /** Capacite exprimee en kilogrammes, pour le taux de remplissage. */
    public BigDecimal capacityKg() {
        return capacityTons == null ? null : capacityTons.multiply(BigDecimal.valueOf(1000));
    }
}
