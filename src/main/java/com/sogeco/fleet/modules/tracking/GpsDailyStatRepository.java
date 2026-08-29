package com.sogeco.fleet.modules.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GpsDailyStatRepository extends JpaRepository<GpsDailyStat, Long> {

    Optional<GpsDailyStat> findByVehicleIdAndStatDate(Long vehicleId, LocalDate statDate);

    List<GpsDailyStat> findByVehicleIdAndStatDateBetweenOrderByStatDateAsc(
            Long vehicleId, LocalDate from, LocalDate to);

    List<GpsDailyStat> findByStatDate(LocalDate statDate);

    @Query("""
           SELECT COALESCE(SUM(s.distanceKm), 0) FROM GpsDailyStat s
           WHERE s.vehicleId = :vehicleId AND s.statDate >= :from AND s.statDate <= :to
           """)
    BigDecimal totalDistance(@Param("vehicleId") Long vehicleId,
                             @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
           SELECT COALESCE(SUM(s.distanceKm), 0) FROM GpsDailyStat s
           WHERE s.statDate >= :from AND s.statDate <= :to
           """)
    BigDecimal fleetDistance(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
