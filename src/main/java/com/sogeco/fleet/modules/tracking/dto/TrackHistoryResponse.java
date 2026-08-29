package com.sogeco.fleet.modules.tracking.dto;

import com.sogeco.fleet.modules.tracking.GpsPosition;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Rejeu d'un trajet : trace, arrets et profil de vitesse. */
public record TrackHistoryResponse(
        Long vehicleId,
        Instant from,
        Instant to,
        int positionCount,
        BigDecimal totalDistanceKm,
        BigDecimal maxSpeedKmh,
        List<Point> points
) {
    public record Point(Instant at, BigDecimal latitude, BigDecimal longitude,
                        BigDecimal speedKmh, BigDecimal heading, Boolean ignitionOn) {

        static Point from(GpsPosition p) {
            return new Point(p.getRecordedAt(), p.getLatitude(), p.getLongitude(),
                    p.getSpeedKmh(), p.getHeading(), p.getIgnitionOn());
        }
    }

    public static TrackHistoryResponse of(Long vehicleId, Instant from, Instant to,
                                          List<GpsPosition> positions) {
        BigDecimal distance = positions.stream()
                .map(GpsPosition::getDistanceKm)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxSpeed = positions.stream()
                .map(GpsPosition::getSpeedKmh)
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return new TrackHistoryResponse(vehicleId, from, to, positions.size(),
                distance, maxSpeed, positions.stream().map(Point::from).toList());
    }
}
