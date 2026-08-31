package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.CarteGriseRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteGriseResponse;
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
@RequestMapping("/api/v1/cartes-grises")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Carte grise", description = "Document d'immatriculation par camion")
public class CarteGriseController {

    private final CarteGriseService service;

    @GetMapping
    @Operation(summary = "Lister les cartes grises")
    public PageResponse<CarteGriseResponse> list(
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique des cartes grises d'un camion")
    public List<CarteGriseResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Cartes grises saisies par le chauffeur connecte — espace chauffeur")
    public List<CarteGriseResponse> mine() {
        return service.mine();
    }

    @PostMapping
    @Operation(summary = "Enregistrer une carte grise")
    public CarteGriseResponse create(@Valid @RequestBody CarteGriseRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corriger une carte grise")
    public CarteGriseResponse update(@PathVariable Long id, @Valid @RequestBody CarteGriseRequest request) {
        return service.update(id, request);
    }
}
