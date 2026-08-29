package com.sogeco.fleet.modules.vehicle;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.vehicle.dto.*;
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
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Camions", description = "Parc de vehicules et affectations")
public class VehicleController {

    private final VehicleService service;
    private final VehicleAssignmentService assignmentService;

    @GetMapping
    @Operation(summary = "Lister les camions")
    public PageResponse<VehicleResponse> list(
            @PageableDefault(size = 20, sort = "registrationNumber", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs par statut et taux de disponibilite")
    public VehicleStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/search")
    @Operation(summary = "Recherche par immatriculation, marque ou modele — barre de recherche du tableau de bord")
    public List<VehicleResponse> search(@RequestParam String q) {
        return service.search(q);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche detaillee, avec documents et echeances")
    public VehicleDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer un camion")
    public ResponseEntity<VehicleDetailResponse> create(@Valid @RequestBody VehicleRequest request) {
        VehicleDetailResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/vehicles/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un camion")
    public VehicleDetailResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut, hors En mission")
    public VehicleDetailResponse changeStatus(@PathVariable Long id, @RequestParam VehicleStatus status) {
        return service.changeStatus(id, status);
    }

    @PatchMapping("/{id}/odometer")
    @Operation(summary = "Corriger le kilometrage — administrateur uniquement")
    public VehicleDetailResponse correctOdometer(@PathVariable Long id,
                                                 @Valid @RequestBody OdometerCorrectionRequest request) {
        return service.correctOdometer(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un camion")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Affectations ----

    @GetMapping("/{id}/assignments")
    @Operation(summary = "Historique des affectations du camion")
    public List<AssignmentResponse> assignments(@PathVariable Long id) {
        return assignmentService.historyForVehicle(id);
    }

    @PostMapping("/{id}/assignments")
    @Operation(summary = "Affecter un chauffeur")
    public AssignmentResponse assign(@PathVariable Long id, @Valid @RequestBody AssignmentRequest request) {
        return assignmentService.assign(id, request);
    }

    @DeleteMapping("/{id}/assignments/current")
    @Operation(summary = "Liberer l'affectation en cours")
    public ResponseEntity<Void> release(@PathVariable Long id) {
        assignmentService.release(id);
        return ResponseEntity.noContent().build();
    }
}
