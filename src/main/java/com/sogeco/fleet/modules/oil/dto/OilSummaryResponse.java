package com.sogeco.fleet.modules.oil.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Etat du fut d'huile pour une ville (ou toutes pour l'administrateur sans filtre) : totaux, stock
 * restant, detail par ville et derniers mouvements.
 */
public record OilSummaryResponse(
        Long cityId,
        BigDecimal totalPurchased,
        BigDecimal totalDistributed,
        BigDecimal remaining,
        List<CityStock> byCity,
        List<Purchase> purchases,
        List<Distribution> distributions
) {

    public record CityStock(Long cityId, String cityName, BigDecimal purchased, BigDecimal distributed,
                            BigDecimal remaining) {
    }

    public record Purchase(Long id, LocalDate date, Long cityId, String cityName, BigDecimal quantityLiters,
                           String note) {
    }

    public record Distribution(Long id, LocalDate date, Long vehicleId, String registrationNumber,
                               String cityName, String recipientName, BigDecimal quantityLiters, String note) {
    }
}
