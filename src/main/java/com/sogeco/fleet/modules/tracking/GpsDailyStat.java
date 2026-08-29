package com.sogeco.fleet.modules.tracking;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Agregat journalier, conserve au-dela des 90 jours de retention.
 *
 * Sans lui, toute analyse historique de kilometrage disparaitrait a la
 * purge des partitions. Volumetrie negligeable : 11 camions par jour.
 */
@Entity
@Table(name = "gps_daily_stats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GpsDailyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Builder.Default
    @Column(name = "distance_km", nullable = false, precision = 10, scale = 2)
    private BigDecimal distanceKm = BigDecimal.ZERO;

    @Column(name = "max_speed_kmh", precision = 6, scale = 2)
    private BigDecimal maxSpeedKmh;

    @Column(name = "avg_speed_kmh", precision = 6, scale = 2)
    private BigDecimal avgSpeedKmh;

    @Builder.Default
    @Column(name = "driving_minutes", nullable = false)
    private Integer drivingMinutes = 0;

    @Builder.Default
    @Column(name = "idle_minutes", nullable = false)
    private Integer idleMinutes = 0;

    @Builder.Default
    @Column(name = "position_count", nullable = false)
    private Integer positionCount = 0;

    @Column(name = "first_position_at")
    private Instant firstPositionAt;

    @Column(name = "last_position_at")
    private Instant lastPositionAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
