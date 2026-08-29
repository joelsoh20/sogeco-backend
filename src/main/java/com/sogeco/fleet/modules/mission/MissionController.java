package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.mission.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Missions", description = "Missions et livraisons")
public class MissionController {

    private final MissionService service;

    @GetMapping
    @Operation(summary = "Lister les missions")
    public PageResponse<MissionResponse> list(
            @PageableDefault(size = 20, sort = "plannedStart", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs de la periode")
    public MissionStatsResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return service.stats(start, end);
    }

    @GetMapping("/mine")
    @Operation(summary = "Missions du chauffeur connecte — espace chauffeur")
    public java.util.List<MissionResponse> mine() {
        return service.myMissions();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche detaillee d'une mission")
    public MissionDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/estimate")
    @Operation(summary = "Estimer la distance et le carburant a partir des villes/sites de depart et d'arrivee")
    public MissionEstimateResponse estimate(
            @RequestParam(required = false) Long originCityId,
            @RequestParam(required = false) Long destinationCityId,
            @RequestParam(required = false) Long originAgencyId,
            @RequestParam(required = false) Long destinationAgencyId,
            @RequestParam(required = false) Long originQuartierId,
            @RequestParam(required = false) Long destinationQuartierId,
            @RequestParam(required = false) Long vehicleId) {
        return service.estimate(originCityId, destinationCityId, originAgencyId, destinationAgencyId,
                originQuartierId, destinationQuartierId, vehicleId);
    }

    @PostMapping
    @Operation(summary = "Creer une mission")
    public ResponseEntity<MissionDetailResponse> create(@Valid @RequestBody MissionRequest request) {
        MissionDetailResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/missions/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une mission non cloturee")
    public MissionDetailResponse update(@PathVariable Long id, @Valid @RequestBody MissionRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Demarrer la mission et passer le camion en mission")
    public MissionDetailResponse start(@PathVariable Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Cloturer et saisir le chiffre d'affaires")
    public MissionDetailResponse complete(@PathVariable Long id,
                                          @Valid @RequestBody MissionCompleteRequest request) {
        return service.complete(id, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler avec motif obligatoire")
    public MissionDetailResponse cancel(@PathVariable Long id,
                                        @Valid @RequestBody MissionCancelRequest request) {
        return service.cancel(id, request);
    }

    @PostMapping("/{id}/detour")
    @Operation(summary = "Signaler un chargement complementaire sur un autre site : ajoute l'etape et corrige la distance prevue")
    public MissionDetailResponse addDetour(@PathVariable Long id,
                                           @Valid @RequestBody MissionDetourRequest request) {
        return service.addDetour(id, request);
    }

    @PostMapping("/{id}/waypoints")
    @Operation(summary = "Ajouter une etape intermediaire")
    public WaypointResponse addWaypoint(@PathVariable Long id,
                                        @Valid @RequestBody WaypointRequest request) {
        return service.addWaypoint(id, request);
    }

    @DeleteMapping("/waypoints/{waypointId}")
    @Operation(summary = "Supprimer une etape")
    public ResponseEntity<Void> deleteWaypoint(@PathVariable Long waypointId) {
        service.deleteWaypoint(waypointId);
        return ResponseEntity.noContent().build();
    }
}
