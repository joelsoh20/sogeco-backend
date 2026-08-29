package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.InsurancePolicyRequest;
import com.sogeco.fleet.modules.insurance.dto.InsurancePolicyResponse;
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
@RequestMapping("/api/v1/insurance-policies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Assurance", description = "Contrats d'assurance, un ou plusieurs camions par police")
public class InsurancePolicyController {

    private final InsurancePolicyService service;

    @GetMapping
    @Operation(summary = "Lister les polices")
    public PageResponse<InsurancePolicyResponse> list(
            @PageableDefault(size = 20, sort = "endDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une police")
    public InsurancePolicyResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Police active couvrant un camion")
    public List<InsurancePolicyResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @PostMapping
    @Operation(summary = "Creer une police, un ou plusieurs camions")
    public ResponseEntity<InsurancePolicyResponse> create(@Valid @RequestBody InsurancePolicyRequest request) {
        InsurancePolicyResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/insurance-policies/" + created.id())).body(created);
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renouveler : l'ancienne police passe a EXPIREE, une nouvelle est creee")
    public InsurancePolicyResponse renew(@PathVariable Long id, @Valid @RequestBody InsurancePolicyRequest request) {
        return service.renew(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Resilier une police")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
