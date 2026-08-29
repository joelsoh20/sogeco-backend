package com.sogeco.fleet.modules.geofence;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.GeofenceZoneType;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.vehicle.Vehicle;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Zone geographique surveillee.
 *
 * Le polygone est stocke en GeoJSON. Le test d'appartenance se fait en
 * Java par lancer de rayon (GeoUtils) : a 11 camions et quelques zones,
 * introduire PostGIS serait disproportionne.
 */
@Entity
@Table(name = "geofence_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeofenceZone extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false, length = 20)
    private GeofenceZoneType zoneType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "polygon_geojson", nullable = false, columnDefinition = "jsonb")
    private String polygonGeojson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Builder.Default
    @Column(name = "alert_on_entry", nullable = false)
    private Boolean alertOnEntry = Boolean.FALSE;

    @Builder.Default
    @Column(name = "alert_on_exit", nullable = false)
    private Boolean alertOnExit = Boolean.TRUE;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * Camions concernes. Ensemble vide : la zone s'applique a tout le
     * parc, ce qui est le cas courant.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "vehicle_geofences",
            joinColumns = @JoinColumn(name = "geofence_zone_id"),
            inverseJoinColumns = @JoinColumn(name = "vehicle_id"))
    private Set<Vehicle> vehicles = new HashSet<>();

    public boolean appliesTo(Long vehicleId) {
        return vehicles.isEmpty()
                || vehicles.stream().anyMatch(vehicle -> vehicle.getId().equals(vehicleId));
    }
}
