package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.event.AlertTriggeredEvent;
import com.sogeco.fleet.common.event.PositionReceivedEvent;
import com.sogeco.fleet.modules.alert.Alert;
import com.sogeco.fleet.modules.alert.AlertRepository;
import com.sogeco.fleet.modules.alert.dto.AlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diffusion des evenements applicatifs vers les canaux WebSocket.
 *
 * Les services metier publient des evenements et ignorent tout du
 * transport : c'est ici, et uniquement ici, que l'on parle de STOMP.
 * Une panne de diffusion n'affecte donc jamais l'ingestion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelematicsBroadcaster {

    public static final String TOPIC_POSITIONS = "/topic/vehicle-positions";
    public static final String TOPIC_POSITION  = "/topic/vehicle-positions/%d";
    public static final String TOPIC_ALERTS    = "/topic/alerts";
    public static final String TOPIC_CRITICAL  = "/topic/alerts/critical";
    public static final String TOPIC_PROGRESS  = "/topic/missions/%d/progress";

    private final SimpMessagingTemplate messaging;
    private final AlertRepository alertRepository;

    @EventListener
    public void onPosition(PositionReceivedEvent event) {
        try {
            messaging.convertAndSend(TOPIC_POSITIONS, event.position());
            messaging.convertAndSend(
                    TOPIC_POSITION.formatted(event.position().vehicleId()), event.position());

            if (event.position().missionId() != null) {
                messaging.convertAndSend(
                        TOPIC_PROGRESS.formatted(event.position().missionId()), event.position());
            }
        } catch (RuntimeException e) {
            log.warn("Diffusion de position impossible", e);
        }
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onAlert(AlertTriggeredEvent event) {
        try {
            Alert alert = alertRepository.findById(event.alertId()).orElse(null);
            if (alert == null) {
                return;
            }

            AlertResponse payload = AlertResponse.from(alert);
            messaging.convertAndSend(TOPIC_ALERTS, payload);

            if (alert.getLevel().requiresImmediateAction()) {
                messaging.convertAndSend(TOPIC_CRITICAL, payload);
            }
        } catch (RuntimeException e) {
            log.warn("Diffusion d'alerte impossible", e);
        }
    }
}
