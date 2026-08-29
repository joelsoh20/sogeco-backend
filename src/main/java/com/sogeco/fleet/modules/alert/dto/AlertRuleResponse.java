package com.sogeco.fleet.modules.alert.dto;

import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.common.enums.ComparisonOperator;
import com.sogeco.fleet.modules.alert.AlertRule;

import java.math.BigDecimal;
import java.util.List;

public record AlertRuleResponse(
        Long id, AlertType alertType, String label,
        BigDecimal thresholdValue, ComparisonOperator comparisonOperator,
        AlertLevel level, Integer cooldownMinutes,
        List<String> notifiedRoles, Boolean realTime, Boolean active) {

    public static AlertRuleResponse from(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(), rule.getAlertType(), rule.getLabel(),
                rule.getThresholdValue(), rule.getComparisonOperator(),
                rule.getLevel(), rule.getCooldownMinutes(),
                rule.notifiedRoles(), rule.getAlertType().isRealTime(), rule.getActive());
    }
}
