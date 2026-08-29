package com.sogeco.fleet.modules.mission;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionAutomationRepository extends JpaRepository<MissionAutomation, Long> {

    @EntityGraph(attributePaths = {"city", "serviceType", "client", "vehicle", "driver", "agency", "destinationQuartier"})
    List<MissionAutomation> findByActiveTrueOrderByIdDesc();

    @EntityGraph(attributePaths = {"city", "serviceType", "client", "vehicle", "driver", "agency", "destinationQuartier"})
    List<MissionAutomation> findAllByOrderByIdDesc();
}
