package com.sogeco.fleet.modules.route;

import com.sogeco.fleet.modules.route.dto.RouteRequest;
import com.sogeco.fleet.modules.route.dto.RouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Corridors", description = "Liaisons inter-agences et valeurs de reference")
public class RouteController {

    private final RouteService service;

    @GetMapping
    @Operation(summary = "Lister les corridors")
    public List<RouteResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un corridor")
    public RouteResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer un corridor")
    public ResponseEntity<RouteResponse> create(@Valid @RequestBody RouteRequest request) {
        RouteResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/routes/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier les valeurs de reference")
    public RouteResponse update(@PathVariable Long id, @Valid @RequestBody RouteRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un corridor")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
