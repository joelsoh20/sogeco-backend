package com.sogeco.fleet.modules.mission.dto;

import com.sogeco.fleet.common.enums.WaypointStatus;
import com.sogeco.fleet.modules.mission.MissionWaypoint;

import java.math.BigDecimal;
import java.time.Instant;

public record WaypointResponse(
        Long id,
        Integer sequenceNumber,
        String label,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant plannedArrival,
        Instant actualArrival,
        WaypointStatus status,
        String notes
) {
    public static WaypointResponse from(MissionWaypoint waypoint) {
        return new WaypointResponse(
                waypoint.getId(), waypoint.getSequenceNumber(), waypoint.getLabel(),
                waypoint.getAddress(), waypoint.getLatitude(), waypoint.getLongitude(),
                waypoint.getPlannedArrival(), waypoint.getActualArrival(),
                waypoint.getStatus(), waypoint.getNotes());
    }
}
