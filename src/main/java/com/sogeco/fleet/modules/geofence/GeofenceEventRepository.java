package com.sogeco.fleet.modules.geofence;

import com.sogeco.fleet.common.enums.GeofenceEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GeofenceEventRepository extends JpaRepository<GeofenceEvent, Long> {

    Page<GeofenceEvent> findByOrderByOccurredAtDesc(Pageable pageable);

    List<GeofenceEvent> findByVehicleIdOrderByOccurredAtDesc(Long vehicleId, Pageable pageable);

    /**
     * Dernier franchissement connu pour un couple camion / zone.
     * Sert a savoir si le camion etait DEDANS ou DEHORS, et donc a ne
     * generer un evenement qu'au changement d'etat.
     */
    @Query("""
           SELECT e FROM GeofenceEvent e
           WHERE e.vehicleId = :vehicleId AND e.geofenceZoneId = :zoneId
           ORDER BY e.occurredAt DESC
           LIMIT 1
           """)
    Optional<GeofenceEvent> findLastEvent(@Param("vehicleId") Long vehicleId,
                                          @Param("zoneId") Long zoneId);

    long countByVehicleIdAndEventType(Long vehicleId, GeofenceEventType eventType);
}
