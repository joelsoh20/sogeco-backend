package com.sogeco.fleet.modules.reporting.dto;

import java.math.BigDecimal;

/** Depense d'un mois (1 = janvier ... 12 = decembre), pour la vue Charges. */
public record MonthlyAmount(int month, BigDecimal amount) {
}
