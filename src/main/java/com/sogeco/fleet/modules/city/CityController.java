package com.sogeco.fleet.modules.city;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.city.dto.CityRequest;
import com.sogeco.fleet.modules.city.dto.CityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cities")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Villes", description = "Referentiel des villes, ouvert et extensible")
public class CityController {

    private final CityService service;

    @GetMapping
    @Operation(summary = "Lister les villes")
    public PageResponse<CityResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/active")
    @Operation(summary = "Lister les villes actives, pour les listes deroulantes")
    public List<CityResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une ville")
    public CityResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer une ville")
    public ResponseEntity<CityResponse> create(@Valid @RequestBody CityRequest request) {
        CityResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/cities/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une ville")
    public CityResponse update(@PathVariable Long id, @Valid @RequestBody CityRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver une ville")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
