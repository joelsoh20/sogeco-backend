package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.insurance.dto.CarteBleueRequest;
import com.sogeco.fleet.modules.insurance.dto.CarteBleueResponse;
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
@RequestMapping("/api/v1/cartes-bleues")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Carte bleue", description = "Carte de circulation par camion")
public class CarteBleueController {

    private final CarteBleueService service;

    @GetMapping
    @Operation(summary = "Lister les cartes bleues")
    public PageResponse<CarteBleueResponse> list(
            @PageableDefault(size = 20, sort = "expiryDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique des cartes bleues d'un camion")
    public List<CarteBleueResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @PostMapping
    @Operation(summary = "Enregistrer une carte bleue")
    public CarteBleueResponse create(@Valid @RequestBody CarteBleueRequest request) {
        return service.create(request);
    }
}
