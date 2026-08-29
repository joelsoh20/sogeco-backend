package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.modules.driver.Driver;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Affectation d'un chauffeur a un camion, historisee.
 *
 * Remplace la relation directe camion <-> chauffeur : un chauffeur peut
 * changer de vehicule sans que l'historique soit perdu (RG-9.4).
 *
 * L'unicite de l'affectation courante est garantie par deux index
 * uniques partiels en base, pas par le code : deux requetes concurrentes
 * ne peuvent pas creer deux affectations actives.
 */
@Entity
@Table(name = "vehicle_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Nul tant que l'affectation est en cours. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedBy;

    @Column(name = "notes", length = 255)
    private String notes;

    public boolean isActive() {
        return endDate == null;
    }

    public void close(LocalDate date) {
        this.endDate = date == null ? LocalDate.now() : date;
    }
}
