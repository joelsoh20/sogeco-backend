package com.sogeco.fleet.modules.reporting.dto;

import com.sogeco.fleet.common.enums.CostCategory;

import java.math.BigDecimal;
import java.util.List;

/** Repartition du cout total par categorie, pour le donut de l'ecran Rapports. */
public record CostBreakdownResponse(
        BigDecimal total,
        List<CategoryAmount> categories
) {
    public record CategoryAmount(CostCategory category, BigDecimal amount, BigDecimal sharePercent) {
    }
}
