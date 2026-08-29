package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.modules.reporting.dto.ExecutiveDashboardResponse;
import com.sogeco.fleet.modules.reporting.dto.OperationalDashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Tableaux de bord", description = "Vue executive et vue operationnelle")
public class DashboardController {

    private final DashboardService service;

    @GetMapping("/executive")
    @Operation(summary = "Vue Direction : KPI consolides, classements, alertes critiques")
    public ExecutiveDashboardResponse executive(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        return service.executive(start, end);
    }

    @GetMapping("/operational")
    @Operation(summary = "Vue du jour : missions, camions immobilises, echeances a 7 jours")
    public OperationalDashboardResponse operational() {
        return service.operational();
    }
}
