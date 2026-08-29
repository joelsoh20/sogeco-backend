package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.TechnicalInspectionRequest;
import com.sogeco.fleet.modules.insurance.dto.TechnicalInspectionResponse;
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
@RequestMapping("/api/v1/technical-inspections")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Visite technique", description = "Controles techniques et leur resultat")
public class TechnicalInspectionController {

    private final TechnicalInspectionService service;

    @GetMapping
    @Operation(summary = "Lister les visites")
    public PageResponse<TechnicalInspectionResponse> list(
            @PageableDefault(size = 20, sort = "inspectionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique des visites d'un camion")
    public List<TechnicalInspectionResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Visites saisies par le chauffeur connecte — espace chauffeur")
    public List<TechnicalInspectionResponse> mine() {
        return service.mine();
    }

    @PostMapping
    @Operation(summary = "Enregistrer une visite technique")
    public TechnicalInspectionResponse create(@Valid @RequestBody TechnicalInspectionRequest request) {
        return service.create(request);
    }
}
