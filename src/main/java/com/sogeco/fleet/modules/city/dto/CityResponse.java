package com.sogeco.fleet.modules.city.dto;

import com.sogeco.fleet.modules.city.City;

import java.math.BigDecimal;

public record CityResponse(
        Long id,
        String code,
        String name,
        String region,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean hasSite,
        Boolean active
) {
    public static CityResponse from(City city) {
        return new CityResponse(
                city.getId(),
                city.getCode(),
                city.getName(),
                city.getRegion(),
                city.getLatitude(),
                city.getLongitude(),
                city.getHasSite(),
                city.getActive());
    }
}
