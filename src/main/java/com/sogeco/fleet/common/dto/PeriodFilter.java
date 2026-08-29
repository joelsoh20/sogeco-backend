package com.sogeco.fleet.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filtre de periode commun aux tableaux de bord et aux rapports.
 * Par defaut : le mois en cours (RG-2.1).
 */
@Schema(description = "Periode d'analyse")
public record PeriodFilter(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
) {

    public PeriodFilter {
        LocalDate today = LocalDate.now();
        if (from == null) from = today.withDayOfMonth(1);
        if (to == null) to = today;
    }

    public boolean isIncomplete() {
        return to.isAfter(LocalDate.now().minusDays(1));
    }
}
