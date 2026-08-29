package com.sogeco.fleet.modules.mission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MissionWaypointRepository extends JpaRepository<MissionWaypoint, Long> {

    List<MissionWaypoint> findByMissionIdOrderBySequenceNumberAsc(Long missionId);

    void deleteByMissionId(Long missionId);
}
