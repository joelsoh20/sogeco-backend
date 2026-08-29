package com.sogeco.fleet.modules.geofence;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.geofence.dto.GeofenceZoneRequest;
import com.sogeco.fleet.modules.geofence.dto.GeofenceZoneResponse;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeofenceService {

    private final GeofenceZoneRepository repository;
    private final GeofenceEventRepository eventRepository;
    private final VehicleRepository vehicleRepository;
    private final CityRepository cityRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GEOFENCE_READ')")
    public List<GeofenceZoneResponse> list() {
        return repository.findByActiveTrueOrderByNameAsc()
                .stream().map(GeofenceZoneResponse::from).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('GEOFENCE_MANAGE')")
    public GeofenceZoneResponse create(GeofenceZoneRequest request) {
        if (repository.existsByNameIgnoreCase(request.name())) {
            throw new BusinessException("RG-11.1",
                    "Une zone porte deja ce nom", HttpStatus.CONFLICT);
        }

        GeofenceZone zone = GeofenceZone.builder()
                .name(request.name())
                .zoneType(request.zoneType())
                .polygonGeojson(request.polygonGeojson())
                .city(request.cityId() == null ? null : cityRepository.findById(request.cityId())
                        .orElseThrow(() -> new ResourceNotFoundException("Ville", request.cityId())))
                .alertOnEntry(Boolean.TRUE.equals(request.alertOnEntry()))
                .alertOnExit(request.alertOnExit() == null || request.alertOnExit())
                .description(request.description())
                .vehicles(resolveVehicles(request))
                .build();

        return GeofenceZoneResponse.from(repository.save(zone));
    }

    @Transactional
    @PreAuthorize("hasAuthority('GEOFENCE_MANAGE')")
    public GeofenceZoneResponse update(Long id, GeofenceZoneRequest request) {
        GeofenceZone zone = find(id);

        zone.setName(request.name());
        zone.setZoneType(request.zoneType());
        zone.setPolygonGeojson(request.polygonGeojson());
        zone.setAlertOnEntry(Boolean.TRUE.equals(request.alertOnEntry()));
        zone.setAlertOnExit(request.alertOnExit() == null || request.alertOnExit());
        zone.setDescription(request.description());
        zone.getVehicles().clear();
        zone.getVehicles().addAll(resolveVehicles(request));

        return GeofenceZoneResponse.from(zone);
    }

    @Transactional
    @PreAuthorize("hasAuthority('GEOFENCE_MANAGE')")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('GEOFENCE_READ')")
    public List<GeofenceEvent> recentEvents(Long vehicleId, int limit) {
        return eventRepository.findByVehicleIdOrderByOccurredAtDesc(
                vehicleId, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    private java.util.Set<com.sogeco.fleet.modules.vehicle.Vehicle> resolveVehicles(
            GeofenceZoneRequest request) {
        if (request.vehicleIds() == null || request.vehicleIds().isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(vehicleRepository.findAllById(request.vehicleIds()));
    }

    private GeofenceZone find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone", id));
    }
}
