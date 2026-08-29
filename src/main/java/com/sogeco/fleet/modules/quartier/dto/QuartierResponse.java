package com.sogeco.fleet.modules.quartier.dto;

import com.sogeco.fleet.modules.quartier.Quartier;

import java.math.BigDecimal;

public record QuartierResponse(
        Long id,
        String name,
        Long cityId,
        String cityName,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean active
) {
    public static QuartierResponse from(Quartier quartier) {
        return new QuartierResponse(
                quartier.getId(),
                quartier.getName(),
                quartier.getCity().getId(),
                quartier.getCity().getName(),
                quartier.getLatitude(),
                quartier.getLongitude(),
                quartier.getActive());
    }
}
