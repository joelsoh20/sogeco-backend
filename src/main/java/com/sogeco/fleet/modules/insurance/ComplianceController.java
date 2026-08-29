package com.sogeco.fleet.modules.insurance;

import com.sogeco.fleet.modules.insurance.dto.ComplianceStatsResponse;
import com.sogeco.fleet.modules.insurance.dto.DeadlineItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Conformite", description = "Echeancier unifie et compteurs de l'ecran conformite")
public class ComplianceController {

    private final ComplianceAnalyticsService analyticsService;

    @GetMapping("/stats")
    @Operation(summary = "Compteurs de tete : polices, sinistres, visites non conformes")
    public ComplianceStatsResponse stats() {
        return analyticsService.stats();
    }

    @GetMapping("/schedule")
    @Operation(summary = "Echeancier unifie : assurances, visites techniques, permis")
    public List<DeadlineItem> schedule(@RequestParam(defaultValue = "90") int daysAhead) {
        return analyticsService.unifiedSchedule(daysAhead);
    }
}
