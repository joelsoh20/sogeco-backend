package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.MissionStatus;
import com.sogeco.fleet.common.enums.VehicleStatus;
import com.sogeco.fleet.modules.alert.AlertRepository;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.insurance.ComplianceAnalyticsService;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.reporting.dto.*;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Tableaux de bord executif et operationnel.
 *
 * Interroge les depots directement plutot que les services de
 * rentabilite ou de missions : ces derniers portent leurs propres
 * permissions (REPORT_READ, MISSION_READ), et un utilisateur habilite
 * uniquement DASHBOARD_EXECUTIVE_READ ne doit jamais se heurter a un
 * refus en cascade parce que le tableau de bord appelle en coulisses
 * un service qu'il n'a pas le droit d'utiliser directement.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");
    private static final List<AlertStatus> OPEN = List.of(AlertStatus.NON_RESOLUE, AlertStatus.EN_COURS);

    private final ReportingService reportingService;
    private final MissionRepository missionRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final AlertRepository alertRepository;
    private final ComplianceAnalyticsService complianceAnalyticsService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DASHBOARD_EXECUTIVE_READ')")
    public ExecutiveDashboardResponse executive(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Object[]> byVehicle = missionRepository.aggregateProfitabilityByVehicle(start, end);
        List<VehicleMarginSummary> vehicleSummaries = byVehicle.stream()
                .map(row -> new VehicleMarginSummary(
                        (Long) row[0], (String) row[1], (BigDecimal) row[5], (Long) row[2]))
                .sorted((a, b) -> b.margin().compareTo(a.margin()))
                .toList();

        List<ClientMarginSummary> clientSummaries = missionRepository.aggregateProfitabilityByClient(start, end)
                .stream()
                .map(row -> new ClientMarginSummary((Long) row[0], (String) row[1], (BigDecimal) row[5]))
                .limit(3)
                .toList();

        return new ExecutiveDashboardResponse(
                from, to,
                reportingService.fleetKpis(from, to, 60),
                vehicleSummaries.stream().limit(3).toList(),
                vehicleSummaries.reversed().stream().limit(3).toList(),
                clientSummaries,
                alertRepository.countByLevelAndStatusIn(AlertLevel.CRITIQUE, OPEN),
                missionRepository.findCompletedWithoutRevenue(Instant.now()).size());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('DASHBOARD_OPERATIONAL_READ')")
    public OperationalDashboardResponse operational() {
        LocalDate today = LocalDate.now(ZONE);
        Instant start = today.atStartOfDay(ZONE).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(ZONE).toInstant();

        return new OperationalDashboardResponse(
                missionRepository.countInPeriod(start, end),
                missionRepository.countByStatusInPeriod(MissionStatus.EN_COURS, start, end),
                missionRepository.countByStatusInPeriod(MissionStatus.EN_ATTENTE, start, end),
                vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.EN_MAINTENANCE),
                vehicleRepository.countByStatusAndActiveTrue(VehicleStatus.EN_PANNE),
                driverRepository.findUnassigned().size(),
                alertRepository.countByStatusIn(OPEN),
                alertRepository.countByLevelAndStatusIn(AlertLevel.CRITIQUE, OPEN),
                complianceAnalyticsService.unifiedSchedule(7));
    }
}
