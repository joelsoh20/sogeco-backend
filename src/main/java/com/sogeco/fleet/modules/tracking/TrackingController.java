package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.modules.tracking.dto.LivePosition;
import com.sogeco.fleet.modules.tracking.dto.TrackHistoryResponse;
import com.sogeco.fleet.modules.tracking.dto.TrackingStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Suivi GPS", description = "Carte temps reel et historique des trajets")
public class TrackingController {

    private final TrackingService service;
    private final WebhookEventRepository webhookRepository;

    @GetMapping("/positions/current")
    @Operation(summary = "Dernieres positions de tout le parc, servies depuis le cache")
    public List<LivePosition> current() {
        return service.currentPositions();
    }

    @GetMapping("/stats")
    @Operation(summary = "Legende de la carte : compteurs par etat")
    public TrackingStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/vehicles/{id}/position")
    @Operation(summary = "Derniere position d'un camion")
    public ResponseEntity<LivePosition> position(@PathVariable Long id) {
        return service.currentPosition(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/vehicles/{id}/history")
    @Operation(summary = "Rejeu d'un trajet sur une periode")
    public TrackHistoryResponse history(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(24, ChronoUnit.HOURS) : from;
        return service.history(id, start, end);
    }

    @GetMapping("/vehicles/{id}/diagnostics")
    @Operation(summary = "Dernieres donnees moteur, si le boitier lit le bus CAN")
    public ResponseEntity<VehicleDiagnostic> diagnostics(@PathVariable Long id) {
        return service.latestDiagnostic(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/webhook-events")
    @PreAuthorize("hasAuthority('INTEGRATION_MANAGE')")
    @Operation(summary = "Journal brut des trames recues, pour diagnostic")
    public PageResponse<WebhookEvent> webhookEvents(@PageableDefault(size = 50) Pageable pageable) {
        return PageResponse.from(webhookRepository.findByOrderByReceivedAtDesc(pageable));
    }
}
