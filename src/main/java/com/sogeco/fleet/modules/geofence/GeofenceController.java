package com.sogeco.fleet.modules.geofence;

import com.sogeco.fleet.modules.geofence.dto.GeofenceZoneRequest;
import com.sogeco.fleet.modules.geofence.dto.GeofenceZoneResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/geofence-zones")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Georeperage", description = "Zones surveillees et franchissements")
public class GeofenceController {

    private final GeofenceService service;

    @GetMapping
    @Operation(summary = "Lister les zones")
    public List<GeofenceZoneResponse> list() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Creer une zone")
    public GeofenceZoneResponse create(@Valid @RequestBody GeofenceZoneRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une zone")
    public GeofenceZoneResponse update(@PathVariable Long id, @Valid @RequestBody GeofenceZoneRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver une zone")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    @Operation(summary = "Derniers franchissements d'un camion")
    public List<GeofenceEvent> events(@RequestParam Long vehicleId,
                                      @RequestParam(defaultValue = "20") int limit) {
        return service.recentEvents(vehicleId, limit);
    }
}
