package com.sogeco.fleet.modules.maintenance;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.MaintenanceStatus;
import com.sogeco.fleet.modules.maintenance.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/maintenance-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Maintenance", description = "Interventions, pannes et comparatif garages")
public class MaintenanceController {

    private final MaintenanceService service;
    private final MaintenanceAnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Lister les interventions")
    public PageResponse<MaintenanceResponse> list(
            @PageableDefault(size = 20, sort = "interventionDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/planned")
    @Operation(summary = "Interventions planifiees")
    public List<MaintenanceResponse> planned() {
        return service.planned();
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique d'un camion")
    public List<MaintenanceResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.forVehicle(vehicleId);
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs, repartition par categorie et comparatif garages")
    public MaintenanceStatsResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return analyticsService.stats(start, end);
    }

    @GetMapping("/stats/by-city")
    @Operation(summary = "Couts et interventions cumules par ville d'affectation des camions")
    public List<MaintenanceCityStatsResponse> statsByCity(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return analyticsService.statsByCity(start, end);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detail avec pieces et prestations")
    public MaintenanceResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Creer une intervention")
    public MaintenanceResponse create(@Valid @RequestBody MaintenanceRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une intervention non terminee")
    public MaintenanceResponse update(@PathVariable Long id, @Valid @RequestBody MaintenanceRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Demarrer, terminer ou annuler")
    public MaintenanceResponse changeStatus(
            @PathVariable Long id,
            @RequestParam MaintenanceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate completionDate) {
        return service.changeStatus(id, status, completionDate);
    }
}
