package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Point mensuel des ecrans Rapports — un mois, premier jour du mois.
 * Sert a la fois la courbe "Evolution des performances" (revenue,
 * cost, margin) et le bar chart empile "Couts par periode"
 * (categories), pour eviter de recalculer deux fois la meme boucle
 * mensuelle.
 */
public record MonthlyFinancialPoint(
        LocalDate month,
        BigDecimal revenue,
        BigDecimal cost,
        BigDecimal margin,
        List<CostBreakdownResponse.CategoryAmount> categories
) {
}
