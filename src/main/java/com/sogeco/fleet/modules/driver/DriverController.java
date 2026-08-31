package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.DriverStatus;
import com.sogeco.fleet.common.enums.RatingCriterion;
import com.sogeco.fleet.modules.alert.AlertQueryService;
import com.sogeco.fleet.modules.alert.dto.AlertResponse;
import com.sogeco.fleet.modules.driver.dto.*;
import com.sogeco.fleet.modules.fuel.FuelAnalyticsService;
import com.sogeco.fleet.modules.vehicle.VehicleAssignmentService;
import com.sogeco.fleet.modules.vehicle.dto.AssignmentResponse;
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
import java.util.List;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Chauffeurs", description = "Dossiers, performance, primes et actions RH")
public class DriverController {

    private final DriverService service;
    private final DriverPerformanceService performanceService;
    private final VehicleAssignmentService assignmentService;
    private final FuelAnalyticsService fuelAnalyticsService;
    private final AlertQueryService alertQueryService;

    // ---- Consultation ----

    @GetMapping
    @Operation(summary = "Lister les chauffeurs")
    public PageResponse<DriverResponse> list(
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/ranking")
    @Operation(summary = "Classement par score de performance")
    public List<DriverResponse> ranking() {
        return service.ranking();
    }

    @GetMapping("/semester-ranking")
    @Operation(summary = "Livraisons et pannes par chauffeur sur le semestre ecoule, a regrouper par ville et type d'usage")
    public List<DriverSemesterRankingResponse> semesterRanking() {
        return service.semesterRanking();
    }

    @GetMapping("/stats")
    @Operation(summary = "Compteurs et repartition par notation, pour la flotte entiere ou une seule ville")
    public DriverStatsResponse stats(@RequestParam(required = false) Long cityId) {
        return service.stats(cityId);
    }

    @GetMapping("/search")
    @Operation(summary = "Recherche par nom ou matricule — barre de recherche du tableau de bord")
    public List<DriverResponse> search(@RequestParam String q) {
        return service.search(q);
    }

    @GetMapping("/unassigned")
    @Operation(summary = "Chauffeurs actifs sans camion affecte")
    public List<DriverResponse> unassigned() {
        return service.unassigned();
    }

    @GetMapping("/me")
    @Operation(summary = "Dossier du chauffeur connecte — espace chauffeur")
    public DriverDetailResponse me() {
        return service.me();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Dossier complet du chauffeur")
    public DriverDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    // ---- Ecriture ----

    @PostMapping
    @Operation(summary = "Creer un chauffeur")
    public ResponseEntity<DriverCreationResult> create(@Valid @RequestBody DriverRequest request) {
        DriverCreationResult created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/drivers/" + created.driver().id())).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un chauffeur")
    public DriverDetailResponse update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut : actif, conge, suspendu, sorti")
    public DriverDetailResponse changeStatus(@PathVariable Long id, @RequestParam DriverStatus status) {
        return service.changeStatus(id, status);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un chauffeur")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ---- Affectations ----

    @GetMapping("/{id}/assignments")
    @Operation(summary = "Historique des camions conduits")
    public List<AssignmentResponse> assignments(@PathVariable Long id) {
        return assignmentService.historyForDriver(id);
    }

    // ---- Performance ----

    @GetMapping("/{id}/ratings")
    @Operation(summary = "Historique des evaluations")
    public List<RatingResponse> ratings(@PathVariable Long id) {
        return performanceService.ratingsOf(id);
    }

    @PostMapping("/{id}/ratings")
    @Operation(summary = "Saisir une note et recalculer le score global")
    public RatingResponse rate(@PathVariable Long id, @Valid @RequestBody RatingRequest request) {
        return performanceService.rate(id, request);
    }

    @GetMapping("/rating-criteria/manual")
    @Operation(summary = "Criteres necessitant une saisie manuelle")
    public List<RatingCriterion> manualCriteria() {
        return performanceService.manualCriteria();
    }

    @GetMapping("/{id}/performance-history")
    @Operation(summary = "Evolution du score global, mois par mois")
    public List<PerformancePoint> performanceHistory(@PathVariable Long id) {
        return performanceService.performanceHistory(id);
    }

    @GetMapping("/{id}/alerts")
    @Operation(summary = "Alertes recentes de ce chauffeur, tous statuts confondus")
    public List<AlertResponse> alerts(@PathVariable Long id) {
        return alertQueryService.recentForDriver(id);
    }

    @GetMapping("/fuel-economy")
    @Operation(summary = "Classement des chauffeurs les plus economes en carburant")
    public List<DriverFuelEconomyResponse> fuelEconomy(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "5") int limit) {
        return fuelAnalyticsService.topEconomyDrivers(from, to, limit);
    }

    // ---- Primes ----

    @GetMapping("/{id}/bonuses")
    @Operation(summary = "Historique des primes")
    public List<BonusResponse> bonuses(@PathVariable Long id) {
        return performanceService.bonusesOf(id);
    }

    @PostMapping("/{id}/bonuses")
    @Operation(summary = "Proposer une prime selon le bareme")
    public BonusResponse proposeBonus(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {
        return performanceService.propose(id, period);
    }

    @PatchMapping("/bonuses/{bonusId}")
    @Operation(summary = "Valider, ajuster, refuser ou marquer versee")
    public BonusResponse decideBonus(@PathVariable Long bonusId,
                                     @Valid @RequestBody BonusDecisionRequest request) {
        return performanceService.decide(bonusId, request);
    }

    // ---- Actions RH ----

    @GetMapping("/{id}/actions")
    @Operation(summary = "Historique des actions RH")
    public List<DriverActionResponse> actions(@PathVariable Long id) {
        return service.actionsOf(id);
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "Avertissement, formation, entretien, felicitation")
    public DriverActionResponse addAction(@PathVariable Long id,
                                          @Valid @RequestBody DriverActionRequest request) {
        return service.addAction(id, request);
    }
}
