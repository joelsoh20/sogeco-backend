package com.sogeco.fleet.modules.alert.dto;

import com.sogeco.fleet.common.enums.AlertType;

import java.math.BigDecimal;
import java.util.List;

/** Compteurs de l'ecran Alertes et Centre de Controle. */
public record AlertStatsResponse(
        long critiques,
        long importantes,
        long mineures,
        long informations,
        long totalActives,
        long resolues,
        long nonResolues,
        BigDecimal tauxResolution,
        Double delaiMoyenResolutionMinutes,
        List<TypeBreakdown> repartitionParType
) {
    public record TypeBreakdown(AlertType alertType, long count, BigDecimal sharePercent) {
    }
}
