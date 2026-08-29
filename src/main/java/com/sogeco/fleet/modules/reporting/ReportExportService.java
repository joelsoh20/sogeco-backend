package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.ReportFormat;
import com.sogeco.fleet.common.enums.ReportType;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.export.CsvWriter;
import com.sogeco.fleet.common.export.XlsxWriter;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.reporting.dto.*;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Production des fichiers de rapport.
 *
 * Chaque type de rapport ne fournit que ses en-tetes et ses lignes ;
 * CsvWriter et XlsxWriter portent seule la logique de mise en forme.
 * Le stockage suit le meme principe que FileStorageService pour les
 * documents : repertoire dedie, nom de fichier en UUID, jamais le nom
 * d'origine sur le disque.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportingService reportingService;
    private final GeneratedReportRepository repository;
    private final UserRepository userRepository;

    @Value("${sogeco.reports.path:./reports}")
    private String storagePath;

    private Path root;

    @PostConstruct
    void init() {
        try {
            this.root = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("Stockage des rapports : {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le dossier de rapports : " + storagePath, e);
        }
    }

    @Transactional
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public GeneratedReport export(ReportType type, ReportFormat format, LocalDate from, LocalDate to) {
        byte[] content = generate(type, format, from, to);
        int rowCount = countRows(type, from, to);

        String fileName = "%s_%s.%s".formatted(type, UUID.randomUUID(), format.name().toLowerCase());
        Path target = root.resolve(fileName);

        try {
            Files.write(target, content);
        } catch (IOException e) {
            throw new BusinessException("REPORT_STORAGE",
                    "Impossible d'enregistrer le rapport", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        GeneratedReport report = repository.save(GeneratedReport.builder()
                .reportType(type)
                .format(format)
                .periodStart(from)
                .periodEnd(to)
                .filePath(fileName)
                .rowCount(rowCount)
                .requestedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .isScheduled(false)
                .build());

        log.info("Rapport {} genere par {} : {} lignes, {}",
                type, SecurityUtils.currentUserEmail(), rowCount, format);

        return report;
    }

    /** Version appelee par la tache planifiee : pas d'utilisateur courant. */
    @Transactional
    public GeneratedReport exportScheduled(ReportType type, ReportFormat format, LocalDate from, LocalDate to) {
        byte[] content = generate(type, format, from, to);
        int rowCount = countRows(type, from, to);

        String fileName = "%s_%s.%s".formatted(type, UUID.randomUUID(), format.name().toLowerCase());
        try {
            Files.write(root.resolve(fileName), content);
        } catch (IOException e) {
            throw new IllegalStateException("Echec d'ecriture du rapport planifie", e);
        }

        return repository.save(GeneratedReport.builder()
                .reportType(type).format(format).periodStart(from).periodEnd(to)
                .filePath(fileName).rowCount(rowCount).isScheduled(true).build());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public byte[] download(Long reportId) {
        GeneratedReport report = repository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Rapport", reportId));
        try {
            return Files.readAllBytes(root.resolve(report.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException("REPORT_STORAGE",
                    "Fichier de rapport introuvable sur le disque", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional(readOnly = true)
    public GeneratedReport find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rapport", id));
    }

    // ------------------------------------------------------------------

    private byte[] generate(ReportType type, ReportFormat format, LocalDate from, LocalDate to) {
        return switch (type) {
            case RENTABILITE_CAMIONS -> writeVehicles(reportingService.vehicleProfitability(from, to), format);
            case RENTABILITE_CLIENTS -> writeClients(reportingService.clientProfitability(from, to), format);
            case RENTABILITE_CORRIDORS -> writeCorridors(reportingService.corridorProfitability(from, to), format);
            case RENTABILITE_AGENCES -> writeAgencies(reportingService.agencyProfitability(from, to), format);
            case SYNTHESE_MENSUELLE -> writeVehicles(reportingService.vehicleProfitability(from, to), format);
            case RENTABILITE_MISSIONS -> throw new BusinessException("REPORT_TYPE",
                    "Export mission par mission non pris en charge, utiliser la liste des missions",
                    HttpStatus.NOT_IMPLEMENTED);
        };
    }

    private int countRows(ReportType type, LocalDate from, LocalDate to) {
        return switch (type) {
            case RENTABILITE_CAMIONS, SYNTHESE_MENSUELLE -> reportingService.vehicleProfitability(from, to).size();
            case RENTABILITE_CLIENTS -> reportingService.clientProfitability(from, to).size();
            case RENTABILITE_CORRIDORS -> reportingService.corridorProfitability(from, to).size();
            case RENTABILITE_AGENCES -> reportingService.agencyProfitability(from, to).size();
            case RENTABILITE_MISSIONS -> 0;
        };
    }

    private static final List<String> VEHICLE_HEADERS = List.of(
            "Immatriculation", "Missions", "Recette", "Cout direct", "Marge directe",
            "Maintenance", "Marge nette", "Marge nette %", "Km parcourus", "Cout / km");

    private byte[] writeVehicles(List<VehicleProfitability> rows, ReportFormat format) {
        if (format == ReportFormat.CSV) {
            return CsvWriter.write(VEHICLE_HEADERS, rows.stream().map(r -> List.of(
                    r.registrationNumber(), String.valueOf(r.missionCount()),
                    str(r.totalRevenue()), str(r.directCost()), str(r.directMargin()),
                    str(r.maintenanceCost()), str(r.netMargin()), str(r.netMarginPercent()),
                    str(r.kmDriven()), str(r.costPerKm()))).toList());
        }
        return XlsxWriter.write("Rentabilite camions", VEHICLE_HEADERS, rows.stream().map(r -> List.<Object>of(
                r.registrationNumber(), r.missionCount(), r.totalRevenue(), r.directCost(),
                r.directMargin(), r.maintenanceCost(), r.netMargin(),
                r.netMarginPercent() == null ? "" : r.netMarginPercent(),
                r.kmDriven(), r.costPerKm() == null ? "" : r.costPerKm())).toList());
    }

    private static final List<String> CLIENT_HEADERS = List.of(
            "Client", "Missions", "Recette", "Cout total", "Marge", "Marge %");

    private byte[] writeClients(List<ClientProfitability> rows, ReportFormat format) {
        if (format == ReportFormat.CSV) {
            return CsvWriter.write(CLIENT_HEADERS, rows.stream().map(r -> List.of(
                    r.clientName(), String.valueOf(r.missionCount()),
                    str(r.totalRevenue()), str(r.totalCost()), str(r.totalMargin()), str(r.marginPercent())))
                    .toList());
        }
        return XlsxWriter.write("Rentabilite clients", CLIENT_HEADERS, rows.stream().map(r -> List.<Object>of(
                r.clientName(), r.missionCount(), r.totalRevenue(), r.totalCost(), r.totalMargin(),
                r.marginPercent() == null ? "" : r.marginPercent())).toList());
    }

    private static final List<String> CORRIDOR_HEADERS = List.of(
            "Corridor", "Missions", "Recette", "Cout total", "Marge", "Km parcourus", "Cout moyen / km");

    private byte[] writeCorridors(List<CorridorProfitability> rows, ReportFormat format) {
        if (format == ReportFormat.CSV) {
            return CsvWriter.write(CORRIDOR_HEADERS, rows.stream().map(r -> List.of(
                    r.routeLabel(), String.valueOf(r.missionCount()),
                    str(r.totalRevenue()), str(r.totalCost()), str(r.totalMargin()),
                    str(r.kmDriven()), str(r.avgCostPerKm()))).toList());
        }
        return XlsxWriter.write("Rentabilite corridors", CORRIDOR_HEADERS, rows.stream().map(r -> List.<Object>of(
                r.routeLabel(), r.missionCount(), r.totalRevenue(), r.totalCost(), r.totalMargin(),
                r.kmDriven(), r.avgCostPerKm() == null ? "" : r.avgCostPerKm())).toList());
    }

    private static final List<String> AGENCY_HEADERS = List.of(
            "Site", "Missions", "Recette", "Cout total", "Marge");

    private byte[] writeAgencies(List<AgencyProfitability> rows, ReportFormat format) {
        if (format == ReportFormat.CSV) {
            return CsvWriter.write(AGENCY_HEADERS, rows.stream().map(r -> List.of(
                    r.agencyName(), String.valueOf(r.missionCount()),
                    str(r.totalRevenue()), str(r.totalCost()), str(r.totalMargin()))).toList());
        }
        return XlsxWriter.write("Rentabilite sites", AGENCY_HEADERS, rows.stream().map(r -> List.<Object>of(
                r.agencyName(), r.missionCount(), r.totalRevenue(), r.totalCost(), r.totalMargin())).toList());
    }

    private String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
