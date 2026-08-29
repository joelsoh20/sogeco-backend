package com.sogeco.fleet.modules.driver.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Un point de la courbe d'evolution du score, pour un mois donne. */
public record PerformancePoint(LocalDate periodMonth, BigDecimal score) {
}
