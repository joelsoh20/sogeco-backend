package com.sogeco.fleet.modules.insurance.dto;

import java.math.BigDecimal;

/** Compteurs de tete de l'ecran Assurance & Visite technique. */
public record ComplianceStatsResponse(
        long totalPolicies,
        long activePolicies,
        long expiringPolicies30Days,
        long totalClaims,
        long openClaims,
        BigDecimal totalEstimatedCost,
        BigDecimal totalReimbursed,
        long nonConformInspections
) {
}
