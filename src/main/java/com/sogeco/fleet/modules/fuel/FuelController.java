package com.sogeco.fleet.modules.fuel;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.fuel.dto.*;
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
@RequestMapping("/api/v1/fuel-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Carburant", description = "Ravitaillements, consommation et anomalies")
public class FuelController {

    private final FuelService service;
    private final FuelAnalyticsService analyticsService;

    @GetMapping
    @Operation(summary = "Lister les pleins")
    public PageResponse<FuelLogResponse> list(
            @PageableDefault(size = 20, sort = "fuelDatetime", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/vehicle/{vehicleId}")
    @Operation(summary = "Historique des pleins d'un camion")
    public List<FuelLogResponse> forVehicle(@PathVariable Long vehicleId) {
        return service.listForVehicle(vehicleId);
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Pleins signales en anomalie")
    public List<FuelLogResponse> anomalies() {
        return service.anomalies();
    }

    @GetMapping("/tank-levels")
    @Operation(summary = "Niveau de carburant estime par camion, optionnellement restreint a une ville")
    public List<TankLevelResponse> tankLevels(@RequestParam(required = false) Long cityId) {
        return analyticsService.tankLevels(cityId);
    }

    @GetMapping("/weekly-refuel")
    @Operation(summary = "Km, consommation et carburant a ajouter par camion sur la periode",
            description = "Pense pour les vehicules a suivi allege (moto, tricycle, voiture de livraison), "
                    + "avant le plein hebdomadaire. Par defaut, semaine en cours (lundi a aujourd'hui).")
    public List<WeeklyRefuelResponse> weeklyRefuel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long cityId) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.with(java.time.DayOfWeek.MONDAY) : from;
        return analyticsService.weeklyRefuel(start, end, cityId);
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs et repartition par camion, optionnellement restreints a une ville")
    public FuelStatsResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long cityId) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return analyticsService.stats(start, end, cityId);
    }

    @PostMapping
    @Operation(summary = "Saisir un plein")
    public FuelLogResponse create(@Valid @RequestBody FuelLogRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Corriger un plein")
    public FuelLogResponse update(@PathVariable Long id, @Valid @RequestBody FuelLogRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler un plein avec motif")
    public FuelLogResponse cancel(@PathVariable Long id, @Valid @RequestBody FuelCancelRequest request) {
        return service.cancel(id, request);
    }
}
