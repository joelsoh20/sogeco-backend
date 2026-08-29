package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.AlertType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    Optional<AlertRule> findByAlertType(AlertType alertType);

    Optional<AlertRule> findByAlertTypeAndActiveTrue(AlertType alertType);

    List<AlertRule> findByActiveTrueOrderByLevelAscLabelAsc();

    List<AlertRule> findAllByOrderByLevelAscLabelAsc();
}
