package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.enums.DriverActionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DriverActionRepository extends JpaRepository<DriverAction, Long> {

    @EntityGraph(attributePaths = "driver")
    List<DriverAction> findByDriverIdOrderByActionDateDesc(Long driverId);

    List<DriverAction> findByDriverIdAndActionType(Long driverId, DriverActionType actionType);

    long countByDriverIdAndActionTypeAndActionDateAfter(
            Long driverId, DriverActionType actionType, LocalDate after);
}
