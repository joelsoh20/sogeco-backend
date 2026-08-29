package com.sogeco.fleet.modules.geofence.dto;

import com.sogeco.fleet.common.enums.GeofenceZoneType;
import com.sogeco.fleet.modules.geofence.GeofenceZone;
import com.sogeco.fleet.modules.vehicle.Vehicle;

import java.util.List;

public record GeofenceZoneResponse(
        Long id, String name, GeofenceZoneType zoneType, String polygonGeojson,
        Long cityId, String cityName,
        Boolean alertOnEntry, Boolean alertOnExit, String description,
        List<String> vehicles, Boolean appliesToAll, Boolean active) {

    public static GeofenceZoneResponse from(GeofenceZone zone) {
        return new GeofenceZoneResponse(
                zone.getId(), zone.getName(), zone.getZoneType(), zone.getPolygonGeojson(),
                zone.getCity() == null ? null : zone.getCity().getId(),
                zone.getCity() == null ? null : zone.getCity().getName(),
                zone.getAlertOnEntry(), zone.getAlertOnExit(), zone.getDescription(),
                zone.getVehicles().stream().map(Vehicle::getRegistrationNumber).sorted().toList(),
                zone.getVehicles().isEmpty(),
                zone.getActive());
    }
}
