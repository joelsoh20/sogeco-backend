package com.sogeco.fleet.modules.quartier;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.quartier.dto.QuartierRequest;
import com.sogeco.fleet.modules.quartier.dto.QuartierResponse;
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
@RequestMapping("/api/v1/quartiers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quartiers", description = "Referentiel des quartiers par ville, ouvert et extensible")
public class QuartierController {

    private final QuartierService service;

    @GetMapping
    @Operation(summary = "Lister les quartiers")
    public PageResponse<QuartierResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/active")
    @Operation(summary = "Lister les quartiers actifs d'une ville, pour les listes deroulantes")
    public List<QuartierResponse> listActive(@RequestParam Long cityId) {
        return service.listActiveForCity(cityId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un quartier")
    public QuartierResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer un quartier")
    public ResponseEntity<QuartierResponse> create(@Valid @RequestBody QuartierRequest request) {
        QuartierResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/quartiers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un quartier")
    public QuartierResponse update(@PathVariable Long id, @Valid @RequestBody QuartierRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un quartier")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
