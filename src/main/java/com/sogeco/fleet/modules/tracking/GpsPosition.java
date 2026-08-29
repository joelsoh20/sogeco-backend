package com.sogeco.fleet.modules.tracking;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Position GPS.
 *
 * Table partitionnee par mois. La cle primaire physique est
 * (id, recorded_at) — exigence de PostgreSQL — mais seul id est declare
 * ici : la sequence etant globale, l'unicite est garantie.
 *
 * Table en ajout seul : n'herite pas de BaseEntity, une position ne se
 * modifie ni ne se supprime individuellement. La purge se fait par
 * DROP de partition.
 */
@Entity
@Table(name = "gps_positions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "mission_id")
    private Long missionId;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kmh", precision = 6, scale = 2)
    private BigDecimal speedKmh;

    @Column(name = "heading", precision = 5, scale = 2)
    private BigDecimal heading;

    @Column(name = "altitude", precision = 8, scale = 2)
    private BigDecimal altitude;

    @Column(name = "ignition_on")
    private Boolean ignitionOn;

    @Column(name = "odometer_km", precision = 12, scale = 2)
    private BigDecimal odometerKm;

    @Column(name = "fuel_level_percent", precision = 5, scale = 2)
    private BigDecimal fuelLevelPercent;

    @Column(name = "fuel_level_liters", precision = 8, scale = 2)
    private BigDecimal fuelLevelLiters;

    /** Distance depuis la position precedente, calculee a l'ingestion. */
    @Column(name = "distance_km", precision = 8, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "protocol", length = 40)
    private String protocol;

    @Builder.Default
    @Column(name = "valid", nullable = false)
    private Boolean valid = Boolean.TRUE;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Camion considere a l'arret en deca du seuil parametre. */
    public boolean isMoving(BigDecimal idleThreshold) {
        return speedKmh != null && speedKmh.compareTo(idleThreshold) > 0;
    }
}
