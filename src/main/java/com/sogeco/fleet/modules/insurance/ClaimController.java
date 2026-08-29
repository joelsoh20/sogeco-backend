package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.ClaimDecisionRequest;
import com.sogeco.fleet.modules.insurance.dto.ClaimRequest;
import com.sogeco.fleet.modules.insurance.dto.ClaimResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Sinistres", description = "Declaration et suivi des sinistres")
public class ClaimController {

    private final ClaimService service;

    @GetMapping
    @Operation(summary = "Lister les sinistres")
    public PageResponse<ClaimResponse> list(
            @PageableDefault(size = 20, sort = "incidentDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Sinistres d'un camion")
    public List<ClaimResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Sinistres declares par le chauffeur connecte — espace chauffeur")
    public List<ClaimResponse> mine() {
        return service.mine();
    }

    @PostMapping
    @Operation(summary = "Declarer un sinistre")
    public ClaimResponse create(@Valid @RequestBody ClaimRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corriger un sinistre dans les 24h suivant sa declaration")
    public ClaimResponse update(@PathVariable Long id, @Valid @RequestBody ClaimRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Faire evoluer le statut, avec montant rembourse le cas echeant")
    public ClaimResponse decide(@PathVariable Long id, @Valid @RequestBody ClaimDecisionRequest request) {
        return service.decide(id, request);
    }
}
