package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.ReportFormat;
import com.sogeco.fleet.common.enums.ReportType;
import com.sogeco.fleet.modules.reporting.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Rapports", description = "Rentabilite par camion, client, corridor, site, et export")
public class ReportController {

    private final ReportingService reportingService;
    private final ReportExportService exportService;
    private final GeneratedReportRepository reportRepository;

    @GetMapping("/vehicles")
    @Operation(summary = "Rentabilite par camion : marge directe et marge nette apres maintenance")
    public List<VehicleProfitability> vehicles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.vehicleProfitability(from, to);
    }

    @GetMapping("/clients")
    @Operation(summary = "Rentabilite par client")
    public List<ClientProfitability> clients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.clientProfitability(from, to);
    }

    @GetMapping("/corridors")
    @Operation(summary = "Rentabilite par corridor")
    public List<CorridorProfitability> corridors(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.corridorProfitability(from, to);
    }

    @GetMapping("/agencies")
    @Operation(summary = "Rentabilite par site")
    public List<AgencyProfitability> agencies(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.agencyProfitability(from, to);
    }

    @GetMapping("/vehicle-expenses")
    @Operation(summary = "Charges de chaque camion, mois par mois et total annuel — missions, entretien, carburant hors mission")
    public List<VehicleExpenseSummary> vehicleExpenses(@RequestParam int year) {
        return reportingService.vehicleExpenses(year);
    }

    @GetMapping("/city-expenses")
    @Operation(summary = "Charges cumulees des camions par ville d'affectation, mois par mois et total annuel")
    public List<CityExpenseSummary> cityExpenses(@RequestParam int year) {
        return reportingService.cityExpenses(year);
    }

    @GetMapping("/cost-breakdown")
    @Operation(summary = "Repartition du cout total par categorie : carburant, maintenance, laverie, assurance, visite technique, peages, frais de mission, autres")
    public CostBreakdownResponse costBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.costBreakdown(from, to);
    }

    @GetMapping("/monthly-trend")
    @Operation(summary = "Evolution mensuelle du chiffre d'affaires, du cout et de la marge")
    public List<MonthlyFinancialPoint> monthlyTrend(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.monthlyTrend(from, to);
    }

    @GetMapping("/fleet-performance")
    @Operation(summary = "Cout carburant et maintenance mensuel, restreint aux villes d'implantation actives")
    public List<FleetPerformancePoint> fleetPerformance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportingService.fleetPerformance(from, to);
    }

    @PostMapping("/export")
    @Operation(summary = "Generer un export CSV ou Excel")
    public ResponseEntity<GeneratedReport> export(
            @RequestParam ReportType type,
            @RequestParam ReportFormat format,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(exportService.export(type, format, from, to));
    }

    @GetMapping("/history")
    @Operation(summary = "Historique des rapports generes")
    public PageResponse<GeneratedReport> history(
            @PageableDefault(size = 20, sort = "generatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(reportRepository.findByOrderByGeneratedAtDesc(pageable));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Telecharger un rapport genere")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        GeneratedReport report = exportService.find(id);
        byte[] content = exportService.download(id);

        MediaType mediaType = report.getFormat() == ReportFormat.CSV
                ? MediaType.parseMediaType("text/csv")
                : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        String extension = report.getFormat().name().toLowerCase();
        String downloadName = "%s_%s_%s.%s".formatted(
                report.getReportType(), report.getPeriodStart(), report.getPeriodEnd(), extension);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(downloadName))
                .contentType(mediaType)
                .body(content);
    }
}
