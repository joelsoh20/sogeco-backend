package com.sogeco.fleet.modules.agency;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.agency.dto.AgencyRequest;
import com.sogeco.fleet.modules.agency.dto.AgencyResponse;
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
@RequestMapping("/api/v1/agencies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sites", description = "Siege, agences et depots")
public class AgencyController {

    private final AgencyService service;

    @GetMapping
    @Operation(summary = "Lister les sites")
    public PageResponse<AgencyResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/active")
    @Operation(summary = "Lister les sites actifs, pour les listes deroulantes")
    public List<AgencyResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un site")
    public AgencyResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer un site")
    public ResponseEntity<AgencyResponse> create(@Valid @RequestBody AgencyRequest request) {
        AgencyResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/agencies/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un site")
    public AgencyResponse update(@PathVariable Long id, @Valid @RequestBody AgencyRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un site")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
