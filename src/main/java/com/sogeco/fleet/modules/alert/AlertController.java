package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.modules.alert.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alertes", description = "Centre de controle et regles de declenchement")
public class AlertController {

    private final AlertQueryService queryService;
    private final AlertService alertService;

    @GetMapping("/alerts")
    @Operation(summary = "Lister les alertes")
    public PageResponse<AlertResponse> list(
            @RequestParam(required = false) AlertLevel level,
            @PageableDefault(size = 20, sort = "triggeredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return level == null ? queryService.list(pageable) : queryService.listByLevel(level, pageable);
    }

    @GetMapping("/alerts/recent")
    @Operation(summary = "Dix dernieres alertes ouvertes, pour le tableau de bord")
    public List<AlertResponse> recent() {
        return queryService.recent();
    }

    @GetMapping("/alerts/stats")
    @Operation(summary = "Compteurs, taux et delai moyen de resolution")
    public AlertStatsResponse stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return queryService.stats(start, end);
    }

    @GetMapping("/alerts/{id}")
    @Operation(summary = "Consulter une alerte")
    public AlertResponse get(@PathVariable Long id) {
        return queryService.get(id);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasAuthority('ALERT_ACKNOWLEDGE')")
    @Operation(summary = "Prendre en compte une alerte")
    public AlertResponse acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }

    @PostMapping("/alerts/{id}/resolve")
    @PreAuthorize("hasAuthority('ALERT_RESOLVE')")
    @Operation(summary = "Resoudre une alerte, note obligatoire")
    public AlertResponse resolve(@PathVariable Long id, @Valid @RequestBody AlertResolveRequest request) {
        return alertService.resolve(id, request.note());
    }

    @PostMapping("/alerts/{id}/ignore")
    @PreAuthorize("hasAuthority('ALERT_RESOLVE')")
    @Operation(summary = "Ignorer une alerte")
    public AlertResponse ignore(@PathVariable Long id, @Valid @RequestBody AlertResolveRequest request) {
        return alertService.ignore(id, request.note());
    }

    @GetMapping("/alert-rules")
    @Operation(summary = "Lister les regles et leurs seuils")
    public List<AlertRuleResponse> rules() {
        return queryService.rules();
    }

    @PutMapping("/alert-rules/{id}")
    @Operation(summary = "Ajuster un seuil ou activer une regle")
    public AlertRuleResponse updateRule(@PathVariable Long id, @Valid @RequestBody AlertRuleRequest request) {
        return queryService.updateRule(id, request);
    }
}
