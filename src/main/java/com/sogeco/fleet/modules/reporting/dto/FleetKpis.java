package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Indicateurs consolides de la flotte sur une periode.
 *
 * utilizationRate : part du temps disponible reellement passee en
 * mission — minutes cumulees en mission / (taille du parc x duree de
 * la periode). C'est la mesure standard d'exploitation d'une flotte.
 *
 * punctualityRate et avgFillRate sont calcules a partir des methodes
 * deja portees par l'entite Mission (isOnTime, fillRate) : aucune
 * logique metier n'est dupliquee ici.
 */
public record FleetKpis(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalRevenue,
        BigDecimal totalDirectCost,
        BigDecimal totalMaintenanceCost,
        BigDecimal netMargin,
        BigDecimal netMarginPercent,
        BigDecimal utilizationRate,
        BigDecimal punctualityRate,
        BigDecimal avgFillRate,
        BigDecimal availabilityRate,
        BigDecimal costPerKm,
        BigDecimal totalKm
) {
}
