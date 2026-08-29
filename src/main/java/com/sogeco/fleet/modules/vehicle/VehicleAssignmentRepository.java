package com.sogeco.fleet.modules.vehicle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleAssignmentRepository extends JpaRepository<VehicleAssignment, Long> {

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    Optional<VehicleAssignment> findByVehicleIdAndEndDateIsNull(Long vehicleId);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    Optional<VehicleAssignment> findByDriverIdAndEndDateIsNull(Long driverId);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    List<VehicleAssignment> findByVehicleIdOrderByStartDateDesc(Long vehicleId);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    List<VehicleAssignment> findByDriverIdOrderByStartDateDesc(Long driverId);

    /** Toutes les affectations en cours, pour la liste des camions. */
    @EntityGraph(attributePaths = {"driver", "vehicle"})
    List<VehicleAssignment> findByEndDateIsNull();
}
