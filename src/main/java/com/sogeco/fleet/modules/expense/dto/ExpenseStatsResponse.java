package com.sogeco.fleet.modules.expense.dto;

import com.sogeco.fleet.common.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseStatsResponse(
        BigDecimal total,
        List<CategoryLine> parCategorie
) {
    public record CategoryLine(ExpenseCategory category, long count,
                               BigDecimal amount, BigDecimal sharePercent) {
    }
}
