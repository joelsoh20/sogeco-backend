package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.CarteRoseRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteRoseResponse;
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
@RequestMapping("/api/v1/cartes-roses")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Carte rose", description = "Attestation d'assurance CEMAC par camion")
public class CarteRoseController {

    private final CarteRoseService service;

    @GetMapping
    @Operation(summary = "Lister les cartes roses")
    public PageResponse<CarteRoseResponse> list(
            @PageableDefault(size = 20, sort = "validTo", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une carte rose")
    public CarteRoseResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique des cartes roses d'un camion")
    public List<CarteRoseResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @GetMapping("/mine")
    @Operation(summary = "Cartes roses saisies par le chauffeur connecte — espace chauffeur")
    public List<CarteRoseResponse> mine() {
        return service.mine();
    }

    @PostMapping
    @Operation(summary = "Enregistrer une carte rose")
    public CarteRoseResponse create(@Valid @RequestBody CarteRoseRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corriger une carte rose")
    public CarteRoseResponse update(@PathVariable Long id, @Valid @RequestBody CarteRoseRequest request) {
        return service.update(id, request);
    }
}
