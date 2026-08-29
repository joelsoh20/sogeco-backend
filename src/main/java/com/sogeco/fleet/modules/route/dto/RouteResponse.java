package com.sogeco.fleet.modules.route.dto;

import com.sogeco.fleet.modules.route.Route;

import java.math.BigDecimal;

public record RouteResponse(
        Long id,
        Long originCityId,
        String originCityName,
        Long destinationCityId,
        String destinationCityName,
        String label,
        BigDecimal referenceDistanceKm,
        Integer referenceDurationMinutes,
        BigDecimal referenceFuelLiters,
        BigDecimal referenceConsumption,
        BigDecimal toleranceKm,
        Boolean hasTrace,
        Boolean active
) {
    public static RouteResponse from(Route route) {
        return new RouteResponse(
                route.getId(),
                route.getOriginCity().getId(),
                route.getOriginCity().getName(),
                route.getDestinationCity().getId(),
                route.getDestinationCity().getName(),
                route.getLabel(),
                route.getReferenceDistanceKm(),
                route.getReferenceDurationMinutes(),
                route.getReferenceFuelLiters(),
                route.referenceConsumption(),
                route.getToleranceKm(),
                route.getCorridorGeojson() != null,
                route.getActive());
    }
}
