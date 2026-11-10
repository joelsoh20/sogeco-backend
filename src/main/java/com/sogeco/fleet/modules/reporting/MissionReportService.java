package com.sogeco.fleet.modules.reporting;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.reporting.dto.MissionReport;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.MissionLine;
import com.sogeco.fleet.modules.reporting.dto.MissionReport.VehicleMissions;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Missions effectuees par les camions sur une periode.
 *
 * Meme convention que les autres rapports : missions TERMINEES dont la cloture tombe dans
 * la periode (fuseau Africa/Douala), perimetre limite a la ville du gestionnaire (RG-13.4).
 * Le cout est le cout total de la mission (carburant, peages, frais, quote-part chauffeur),
 * la marge celle enregistree sur la mission.
 */
@Service
@RequiredArgsConstructor
public class MissionReportService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Douala");

    private final EntityManager em;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public MissionReport build(LocalDate from, LocalDate to, Collection<Long> vehicleIds) {
        if (to.isBefore(from)) {
            throw new BusinessException("REPORT_PERIOD",
                    "La date de fin precede la date de debut", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        boolean allVehicles = vehicleIds == null || vehicleIds.isEmpty();

        List<Mission> missions = em.createQuery("""
                SELECT m FROM Mission m
                JOIN FETCH m.vehicle v
                JOIN FETCH m.serviceType
                JOIN FETCH m.driver
                LEFT JOIN FETCH m.agency
                LEFT JOIN FETCH m.destinationAgency
                LEFT JOIN FETCH m.originCity
                LEFT JOIN FETCH m.destinationCity
                WHERE m.status = com.sogeco.fleet.common.enums.MissionStatus.TERMINEE
                  AND m.actualEnd >= :from AND m.actualEnd < :to
                  AND (:cityId IS NULL OR v.city.id = :cityId)
                ORDER BY v.registrationNumber, m.actualEnd""", Mission.class)
                .setParameter("from", start).setParameter("to", end).setParameter("cityId", cityId)
                .getResultList();

        Map<Long, List<Mission>> byVehicle = new LinkedHashMap<>();
        for (Mission m : missions) {
            if (allVehicles || vehicleIds.contains(m.getVehicle().getId())) {
                byVehicle.computeIfAbsent(m.getVehicle().getId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<VehicleMissions> sections = new ArrayList<>();
        long count = 0;
        BigDecimal km = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal margin = BigDecimal.ZERO;

        for (List<Mission> list : byVehicle.values()) {
            var vehicle = list.get(0).getVehicle();
            List<MissionLine> lines = list.stream().map(MissionReportService::toLine).toList();
            BigDecimal vKm = sum(lines, MissionLine::distanceKm);
            BigDecimal vRevenue = sum(lines, MissionLine::revenue);
            BigDecimal vCost = sum(lines, MissionLine::cost);
            BigDecimal vMargin = sum(lines, MissionLine::margin);
            sections.add(new VehicleMissions(vehicle.getId(), vehicle.getRegistrationNumber(),
                    ((vehicle.getBrand() == null ? "" : vehicle.getBrand()) + " "
                            + (vehicle.getModel() == null ? "" : vehicle.getModel())).trim(),
                    lines.size(), vKm, vRevenue, vCost, vMargin, lines));
            count += lines.size();
            km = km.add(vKm);
            revenue = revenue.add(vRevenue);
            cost = cost.add(vCost);
            margin = margin.add(vMargin);
        }
        sections.sort(Comparator.comparing(VehicleMissions::registrationNumber));

        return new MissionReport(from, to, Instant.now(), SecurityUtils.currentUserEmail(),
                count, km, revenue, cost, margin, sections);
    }

    private static MissionLine toLine(Mission m) {
        String origin = firstNonBlank(m.getDepartureAddress(),
                m.getAgency() == null ? null : m.getAgency().getName(),
                m.getOriginCity() == null ? null : m.getOriginCity().getName());
        String destination = firstNonBlank(m.getDestinationAddress(),
                m.getDestinationAgency() == null ? null : m.getDestinationAgency().getName(),
                m.getDestinationCity() == null ? null : m.getDestinationCity().getName());
        return new MissionLine(m.getMissionNumber(), m.getActualStart(), m.getActualEnd(),
                m.getServiceType().getLabel(), origin, destination, m.getDriver().getFullName(),
                nz(m.getDistanceKm()), nz(m.getRevenueAmount()), nz(m.getTotalCost()), nz(m.getMarginAmount()));
    }

    private static BigDecimal sum(List<MissionLine> lines, java.util.function.Function<MissionLine, BigDecimal> f) {
        return lines.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "—";
    }
}
