package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.geofence.GeofenceZone;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Alerte.
 *
 * Le champ occurrences absorbe les repetitions : une meme anomalie
 * dans la fenetre de cooldown incremente le compteur au lieu de creer
 * une nouvelle ligne. Trop d'alertes tuent l'alerte.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_rule_id")
    private AlertRule rule;

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
    @JoinColumn(name = "geofence_zone_id")
    private GeofenceZone geofenceZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private AlertLevel level;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Builder.Default
    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt = Instant.now();

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "location_label", length = 255)
    private String locationLabel;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlertStatus status = AlertStatus.NON_RESOLUE;

    @Builder.Default
    @Column(name = "occurrences", nullable = false)
    private Integer occurrences = 1;

    @Column(name = "last_occurrence_at")
    private Instant lastOccurrenceAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by_user_id")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    // ------------------------------------------------------------------

    public void registerOccurrence() {
        this.occurrences = this.occurrences + 1;
        this.lastOccurrenceAt = Instant.now();
    }

    public void acknowledge(User user) {
        this.status = AlertStatus.EN_COURS;
        this.acknowledgedBy = user;
        this.acknowledgedAt = Instant.now();
    }

    public void resolve(User user, String note) {
        this.status = AlertStatus.RESOLUE;
        this.resolvedBy = user;
        this.resolvedAt = Instant.now();
        this.resolutionNote = note;
    }

    /**
     * Anciennete affichee en clair sur l'ecran Alertes :
     * "Non resolue depuis 3 min", "En cours depuis 45 min".
     */
    public Long ageMinutes() {
        Instant reference = status == AlertStatus.EN_COURS && acknowledgedAt != null
                ? acknowledgedAt
                : triggeredAt;
        return Duration.between(reference, Instant.now()).toMinutes();
    }

    /** Delai de traitement, pour le suivi de la reactivite. */
    public Long resolutionMinutes() {
        return resolvedAt == null ? null : Duration.between(triggeredAt, resolvedAt).toMinutes();
    }

    public boolean isOpen() {
        return status.isOpen();
    }
}
