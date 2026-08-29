package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.CancellationReason;
import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.modules.agency.Agency;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.client.Client;
import com.sogeco.fleet.modules.client.ServiceType;
import com.sogeco.fleet.modules.client.Tariff;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.route.Route;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Mission de transport.
 *
 * Unite d'analyse centrale : sans mission correctement cloturee, ni la
 * rentabilite, ni la performance des chauffeurs, ni les couts par
 * corridor ne sont calculables.
 *
 * total_cost et margin_amount sont des colonnes calculees par la base :
 * elles ne peuvent pas diverger des composantes.
 */
@Entity
@Table(name = "missions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mission extends BaseEntity {

    @Column(name = "mission_number", nullable = false, length = 30, unique = true)
    private String missionNumber;

    // ---- Rattachements ----

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agency_id")
    private Agency agency;

    /** Site d'arrivee, distinct du site de depart (agency) — optionnel, pour un calcul de distance site-a-site. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_agency_id")
    private Agency destinationAgency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id")
    private Route route;

    /** Automatisation ayant genere cette mission, si elle vient d'une livraison quotidienne recurrente. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_automation_id")
    private MissionAutomation missionAutomation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origin_city_id")
    private City originCity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_city_id")
    private City destinationCity;

    // ---- Trajet ----

    @Column(name = "departure_address", length = 255)
    private String departureAddress;

    @Column(name = "destination_address", length = 255)
    private String destinationAddress;

    @Column(name = "departure_latitude", precision = 10, scale = 7)
    private BigDecimal departureLatitude;

    @Column(name = "departure_longitude", precision = 10, scale = 7)
    private BigDecimal departureLongitude;

    @Column(name = "destination_latitude", precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    /** Distance reelle, mesuree par le GPS et non theorique (RG-5.10). */
    @Column(name = "distance_km", precision = 10, scale = 2)
    private BigDecimal distanceKm;

    // ---- Dates ----

    @Column(name = "planned_start")
    private Instant plannedStart;

    @Column(name = "planned_arrival")
    private Instant plannedArrival;

    @Column(name = "actual_start")
    private Instant actualStart;

    @Column(name = "actual_end")
    private Instant actualEnd;

    // ---- Chargement (saisie globale) ----

    @Column(name = "cargo_description", length = 255)
    private String cargoDescription;

    @Column(name = "cargo_weight_kg", precision = 10, scale = 2)
    private BigDecimal cargoWeightKg;

    @Column(name = "cargo_volume_m3", precision = 10, scale = 2)
    private BigDecimal cargoVolumeM3;

    // ---- Financier ----

    @Builder.Default
    @Column(name = "revenue_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal revenueAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tariff_id")
    private Tariff tariff;

    @Column(name = "revenue_note", length = 255)
    private String revenueNote;

    @Builder.Default
    @Column(name = "fuel_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal fuelCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "toll_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal tollCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "driver_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal driverCost = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "other_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal otherCost = BigDecimal.ZERO;

    /**
     * Indemnite forfaitaire de voyage (nourriture, hebergement...), distincte
     * de driver_cost (quote-part de salaire imputee automatiquement a la
     * cloture) : connue des la planification, saisie a la creation.
     */
    @Builder.Default
    @Column(name = "mission_fee_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal missionFeeCost = BigDecimal.ZERO;

    /** Colonnes calculees par PostgreSQL, jamais ecrites par l'application. */
    @Column(name = "total_cost", insertable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Column(name = "margin_amount", insertable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal marginAmount;

    // ---- Suivi ----

    @Builder.Default
    @Column(name = "progress", nullable = false, precision = 5, scale = 2)
    private BigDecimal progress = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MissionStatus status = MissionStatus.EN_ATTENTE;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private CancellationReason cancellationReason;

    @Column(name = "cancellation_comment", length = 500)
    private String cancellationComment;

    /** Numero de bon de livraison du logiciel de stock (decision D15). */
    @Column(name = "external_reference", length = 60)
    private String externalReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;

    // ------------------------------------------------------------------
    // Comportement
    // ------------------------------------------------------------------

    public void start() {
        this.status = MissionStatus.EN_COURS;
        this.actualStart = Instant.now();
    }

    public void complete() {
        this.status = MissionStatus.TERMINEE;
        this.actualEnd = Instant.now();
        this.progress = BigDecimal.valueOf(100);
    }

    public void cancel(CancellationReason reason, String comment) {
        this.status = MissionStatus.ANNULEE;
        this.cancellationReason = reason;
        this.cancellationComment = comment;
    }

    /** Duree reelle en minutes, une fois la mission terminee. */
    public Long actualDurationMinutes() {
        if (actualStart == null || actualEnd == null) {
            return null;
        }
        return Duration.between(actualStart, actualEnd).toMinutes();
    }

    /**
     * Ponctualite (RG-12 : taux de ponctualite).
     * Nul tant que la mission n'est pas terminee ou sans arrivee prevue.
     */
    public Boolean isOnTime(int marginMinutes) {
        if (actualEnd == null || plannedArrival == null) {
            return null;
        }
        return !actualEnd.isAfter(plannedArrival.plusSeconds(marginMinutes * 60L));
    }

    /** Taux de remplissage : poids transporte rapporte a la capacite. */
    public BigDecimal fillRate() {
        BigDecimal capacity = vehicle == null ? null : vehicle.capacityKg();
        if (capacity == null || capacity.signum() == 0 || cargoWeightKg == null) {
            return null;
        }
        return cargoWeightKg.multiply(BigDecimal.valueOf(100))
                .divide(capacity, 1, java.math.RoundingMode.HALF_UP);
    }

    /** Une mission facturable sans montant fausse tous les rapports. */
    public boolean isRevenueMissing() {
        return Boolean.TRUE.equals(serviceType.getBillable())
                && (revenueAmount == null || revenueAmount.signum() == 0);
    }

    /**
     * Lieu de depart affichable : ville pour un voyage hors ville, site pour
     * une activite en ville (RG-3.x), adresse libre en dernier recours.
     */
    public String originLabel() {
        if (originCity != null) return originCity.getName();
        if (agency != null) return agency.getName();
        return departureAddress;
    }

    /** Meme logique que originLabel(), cote destination. */
    public String destinationLabel() {
        if (destinationCity != null) return destinationCity.getName();
        if (destinationAgency != null) return destinationAgency.getName();
        return destinationAddress;
    }
}
