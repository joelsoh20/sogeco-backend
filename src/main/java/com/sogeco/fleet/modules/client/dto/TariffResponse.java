package com.sogeco.fleet.modules.client.dto;

import com.sogeco.fleet.common.enums.PricingMode;
import com.sogeco.fleet.modules.client.Tariff;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TariffResponse(
        Long id,
        Long clientId,
        String clientName,
        Long serviceTypeId,
        String serviceTypeLabel,
        Long routeId,
        String routeLabel,
        PricingMode pricingMode,
        BigDecimal unitPrice,
        BigDecimal minAmount,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean active
) {
    public static TariffResponse from(Tariff tariff) {
        return new TariffResponse(
                tariff.getId(),
                tariff.getClient() == null ? null : tariff.getClient().getId(),
                tariff.getClient() == null ? "Tarif general" : tariff.getClient().getCompanyName(),
                tariff.getServiceType().getId(),
                tariff.getServiceType().getLabel(),
                tariff.getRoute() == null ? null : tariff.getRoute().getId(),
                tariff.getRoute() == null ? "Tous corridors" : tariff.getRoute().getLabel(),
                tariff.getPricingMode(),
                tariff.getUnitPrice(),
                tariff.getMinAmount(),
                tariff.getValidFrom(),
                tariff.getValidTo(),
                tariff.getActive());
    }
}
