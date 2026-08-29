package com.sogeco.fleet.modules.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VehicleDiagnosticRepository extends JpaRepository<VehicleDiagnostic, Long> {

    @Query("""
           SELECT d FROM VehicleDiagnostic d
           WHERE d.vehicleId = :vehicleId
           ORDER BY d.recordedAt DESC
           LIMIT 1
           """)
    Optional<VehicleDiagnostic> findLatest(@Param("vehicleId") Long vehicleId);

    List<VehicleDiagnostic> findByVehicleIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long vehicleId, Instant from, Instant to);

    List<VehicleDiagnostic> findByDtcCountGreaterThanOrderByRecordedAtDesc(Integer minimum);
}
