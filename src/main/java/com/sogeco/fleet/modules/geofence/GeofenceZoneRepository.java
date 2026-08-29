package com.sogeco.fleet.modules.geofence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeofenceZoneRepository extends JpaRepository<GeofenceZone, Long> {

    @EntityGraph(attributePaths = {"vehicles", "city"})
    List<GeofenceZone> findByActiveTrueOrderByNameAsc();

    @EntityGraph(attributePaths = "vehicles")
    List<GeofenceZone> findByActiveTrue();

    boolean existsByNameIgnoreCase(String name);
}
