package com.sogeco.fleet.modules.expense;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.agency.AgencyRepository;
import com.sogeco.fleet.modules.document.DocumentRepository;
import com.sogeco.fleet.modules.driver.DriverRepository;
import com.sogeco.fleet.modules.expense.dto.ExpenseRequest;
import com.sogeco.fleet.modules.expense.dto.ExpenseResponse;
import com.sogeco.fleet.modules.expense.dto.ExpenseStatsResponse;
import com.sogeco.fleet.modules.mission.Mission;
import com.sogeco.fleet.modules.mission.MissionRepository;
import com.sogeco.fleet.modules.partner.PartnerRepository;
import com.sogeco.fleet.modules.user.UserRepository;
import com.sogeco.fleet.modules.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Depenses diverses : salaires, peages, amendes, administratif.
 *
 * Sans cette table, la repartition des couts du tableau de bord serait
 * incalculable. Une depense rattachee a une mission est imputee a son
 * cout direct.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository repository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final MissionRepository missionRepository;
    private final AgencyRepository agencyRepository;
    private final PartnerRepository partnerRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FINANCE_READ') or hasAuthority('MAINTENANCE_READ')")
    public PageResponse<ExpenseResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public ExpenseStatsResponse stats(LocalDate from, LocalDate to) {
        BigDecimal total = repository.totalAmount(from, to);

        List<ExpenseStatsResponse.CategoryLine> lines = new ArrayList<>();
        for (Object[] row : repository.aggregateByCategory(from, to)) {
            BigDecimal amount = (BigDecimal) row[2];
            BigDecimal share = total.signum() == 0
                    ? BigDecimal.ZERO
                    : amount.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);

            lines.add(new ExpenseStatsResponse.CategoryLine(
                    (com.sogeco.fleet.common.enums.ExpenseCategory) row[0],
                    (Long) row[1], amount, share));
        }

        return new ExpenseStatsResponse(total, lines);
    }

    @Transactional
    @PreAuthorize("hasAuthority('FINANCE_READ') or hasAuthority('MAINTENANCE_CREATE')")
    public ExpenseResponse create(ExpenseRequest request) {
        Mission mission = request.missionId() == null ? null
                : missionRepository.findById(request.missionId())
                    .orElseThrow(() -> new ResourceNotFoundException("Mission", request.missionId()));

        Expense expense = Expense.builder()
                .expenseDate(request.expenseDate())
                .category(request.category())
                .label(request.label())
                .amount(request.amount())
                .vehicle(request.vehicleId() == null ? null : vehicleRepository.findById(request.vehicleId())
                        .orElseThrow(() -> new ResourceNotFoundException("Camion", request.vehicleId())))
                .driver(request.driverId() == null ? null : driverRepository.findById(request.driverId())
                        .orElseThrow(() -> new ResourceNotFoundException("Chauffeur", request.driverId())))
                .mission(mission)
                .agency(request.agencyId() == null ? null
                        : agencyRepository.findById(request.agencyId()).orElse(null))
                .partner(request.partnerId() == null ? null
                        : partnerRepository.findById(request.partnerId()).orElse(null))
                .document(request.documentId() == null ? null
                        : documentRepository.findById(request.documentId()).orElse(null))
                .createdByUser(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .notes(request.notes())
                .build();

        if (request.expenseDate().isAfter(LocalDate.now())) {
            throw new BusinessException("RG-11.1",
                    "La date de la depense est dans le futur", HttpStatus.UNPROCESSABLE_CONTENT);
        }

        Expense saved = repository.save(expense);

        // Imputation au cout direct de la mission (peage, manutention, amende).
        if (saved.isDirectMissionCost()) {
            switch (saved.getCategory()) {
                case PEAGE -> mission.setTollCost(mission.getTollCost().add(saved.getAmount()));
                default -> mission.setOtherCost(mission.getOtherCost().add(saved.getAmount()));
            }
        }

        return ExpenseResponse.from(saved);
    }

    @Transactional
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public void delete(Long id) {
        Expense expense = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Depense", id));

        if (expense.isDirectMissionCost()) {
            Mission mission = expense.getMission();
            switch (expense.getCategory()) {
                case PEAGE -> mission.setTollCost(
                        mission.getTollCost().subtract(expense.getAmount()).max(BigDecimal.ZERO));
                default -> mission.setOtherCost(
                        mission.getOtherCost().subtract(expense.getAmount()).max(BigDecimal.ZERO));
            }
        }

        repository.delete(expense);
    }
}
