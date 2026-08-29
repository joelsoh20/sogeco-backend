package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Page<WebhookEvent> findByOrderByReceivedAtDesc(Pageable pageable);

    Page<WebhookEvent> findByStatusOrderByReceivedAtDesc(WebhookStatus status, Pageable pageable);

    List<WebhookEvent> findByDeviceIdOrderByReceivedAtDesc(String deviceId, Pageable pageable);

    long countByStatusAndReceivedAtAfter(WebhookStatus status, Instant since);

    /** Purge hebdomadaire : le journal brut n'a pas vocation a etre conserve. */
    @Modifying
    @Query("DELETE FROM WebhookEvent e WHERE e.receivedAt < :before")
    int purgeOlderThan(@Param("before") Instant before);
}
