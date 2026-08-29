package com.sogeco.fleet.modules.maintenance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceItemRepository extends JpaRepository<MaintenanceItem, Long> {

    List<MaintenanceItem> findByMaintenanceLogIdOrderByItemTypeAscLabelAsc(Long maintenanceLogId);
}
