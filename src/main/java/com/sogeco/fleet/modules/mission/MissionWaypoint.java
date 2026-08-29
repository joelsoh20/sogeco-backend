package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.WaypointStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Etape intermediaire d'une tournee desservant plusieurs points. */
@Entity
@Table(name = "mission_waypoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissionWaypoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "planned_arrival")
    private Instant plannedArrival;

    @Column(name = "actual_arrival")
    private Instant actualArrival;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WaypointStatus status = WaypointStatus.EN_ATTENTE;

    @Column(name = "notes", length = 255)
    private String notes;
}
