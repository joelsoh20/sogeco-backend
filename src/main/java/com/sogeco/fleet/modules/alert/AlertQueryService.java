package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.modules.alert.dto.*;
import com.sogeco.fleet.modules.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertQueryService {

    private static final List<AlertStatus> OPEN =
            List.of(AlertStatus.NON_RESOLUE, AlertStatus.EN_COURS);

    private final AlertRepository repository;
    private final AlertRuleRepository ruleRepository;
    private final SettingService settingService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public PageResponse<AlertResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), AlertResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public PageResponse<AlertResponse> listByLevel(AlertLevel level, Pageable pageable) {
        return PageResponse.from(
                repository.findByLevelOrderByTriggeredAtDesc(level, pageable), AlertResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public List<AlertResponse> recent() {
        return repository.findTop10ByStatusInOrderByTriggeredAtDesc(OPEN)
                .stream().map(AlertResponse::from).toList();
    }

    /**
     * Alertes recentes d'un chauffeur precis, tous statuts confondus
     * — pour l'ecran Chauffeurs et Performance. Contrairement a
     * recent(), pas de filtre sur les statuts ouverts : une alerte
     * deja resolue reste pertinente dans l'historique individuel d'un
     * chauffeur, meme si elle a disparu du flux operationnel general.
     */
    public List<AlertResponse> recentForDriver(Long driverId) {
        return repository.findTop10ByDriverIdOrderByTriggeredAtDesc(driverId)
                .stream().map(AlertResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public AlertResponse get(Long id) {
        return AlertResponse.from(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte", id)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public AlertStatsResponse stats(LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(settingService.getString("company.timezone", "Africa/Douala"));
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        long resolved = 0;
        long unresolved = 0;
        for (Object[] row : repository.countByStatus(start, end)) {
            AlertStatus status = (AlertStatus) row[0];
            long count = (Long) row[1];
            if (status == AlertStatus.RESOLUE || status == AlertStatus.IGNOREE) {
                resolved += count;
            } else {
                unresolved += count;
            }
        }

        long total = resolved + unresolved;

        List<AlertStatsResponse.TypeBreakdown> byType = new ArrayList<>();
        for (Object[] row : repository.countByType(start, end)) {
            long count = (Long) row[1];
            byType.add(new AlertStatsResponse.TypeBreakdown(
                    (AlertType) row[0], count, percentage(count, total)));
        }

        return new AlertStatsResponse(
                repository.countByLevelAndStatusIn(AlertLevel.CRITIQUE, OPEN),
                repository.countByLevelAndStatusIn(AlertLevel.IMPORTANT, OPEN),
                repository.countByLevelAndStatusIn(AlertLevel.MINEUR, OPEN),
                repository.countByLevelAndStatusIn(AlertLevel.INFORMATION, OPEN),
                repository.countByStatusIn(OPEN),
                resolved,
                unresolved,
                percentage(resolved, total),
                repository.averageResolutionMinutes(start, end),
                byType);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public List<AlertRuleResponse> rules() {
        return ruleRepository.findAllByOrderByLevelAscLabelAsc()
                .stream().map(AlertRuleResponse::from).toList();
    }

    /** Ajustement d'un seuil : aucun redeploiement necessaire (RG-10.3). */
    @Transactional
    @PreAuthorize("hasAuthority('ALERT_RULE_MANAGE')")
    public AlertRuleResponse updateRule(Long id, AlertRuleRequest request) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regle d'alerte", id));

        rule.setThresholdValue(request.thresholdValue());
        rule.setComparisonOperator(request.comparisonOperator());
        rule.setLevel(request.level());
        if (request.cooldownMinutes() != null) {
            rule.setCooldownMinutes(request.cooldownMinutes());
        }
        rule.setNotifyRoleCodes(request.notifyRoleCodes());
        rule.setActive(request.active());

        return AlertRuleResponse.from(rule);
    }

    private BigDecimal percentage(long part, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
