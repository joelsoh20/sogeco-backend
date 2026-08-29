package com.sogeco.fleet.modules.partner;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.PartnerType;
import com.sogeco.fleet.modules.partner.dto.PartnerRequest;
import com.sogeco.fleet.modules.partner.dto.PartnerResponse;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Partenaires", description = "Garages, stations, assureurs, centres de visite")
public class PartnerController {

    private final PartnerService service;

    @GetMapping
    @Operation(summary = "Lister les partenaires")
    public PageResponse<PartnerResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/active")
    @Operation(summary = "Partenaires actifs, filtrables par type")
    public List<PartnerResponse> listActive(@RequestParam(required = false) PartnerType type) {
        return type == null ? service.listActive() : service.listByType(type);
    }

    @PostMapping
    @Operation(summary = "Creer un partenaire")
    public PartnerResponse create(@Valid @RequestBody PartnerRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un partenaire")
    public PartnerResponse update(@PathVariable Long id, @Valid @RequestBody PartnerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un partenaire")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
