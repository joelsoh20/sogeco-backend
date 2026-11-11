package com.sogeco.fleet.modules.oil;

import com.sogeco.fleet.modules.oil.dto.OilDistributionRequest;
import com.sogeco.fleet.modules.oil.dto.OilPurchaseRequest;
import com.sogeco.fleet.modules.oil.dto.OilSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/oil")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Huile moteur", description = "Suivi du fut d'huile par ville : approvisionnements et huile donnee aux camions")
public class OilController {

    private final OilService service;

    @GetMapping("/summary")
    @Operation(summary = "Stock d'huile restant, totaux et derniers mouvements (cityId : filtre pour l'administrateur)")
    public OilSummaryResponse summary(@RequestParam(required = false) Long cityId) {
        return service.summary(cityId);
    }

    @PostMapping("/purchases")
    @Operation(summary = "Enregistrer un approvisionnement en huile (date et quantite)")
    public OilSummaryResponse purchase(@Valid @RequestBody OilPurchaseRequest request) {
        return service.purchase(request);
    }

    @PutMapping("/purchases/{id}")
    @Operation(summary = "Corriger un approvisionnement en huile")
    public OilSummaryResponse updatePurchase(@PathVariable Long id, @Valid @RequestBody OilPurchaseRequest request) {
        return service.updatePurchase(id, request);
    }

    @PostMapping("/distributions")
    @Operation(summary = "Donner de l'huile a un camion")
    public OilSummaryResponse distribute(@Valid @RequestBody OilDistributionRequest request) {
        return service.distribute(request);
    }

    @PutMapping("/distributions/{id}")
    @Operation(summary = "Corriger une sortie d'huile")
    public OilSummaryResponse updateDistribution(@PathVariable Long id, @Valid @RequestBody OilDistributionRequest request) {
        return service.updateDistribution(id, request);
    }
}
