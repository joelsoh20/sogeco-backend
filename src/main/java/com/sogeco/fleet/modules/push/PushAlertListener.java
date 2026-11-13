package com.sogeco.fleet.modules.push;

import com.sogeco.fleet.common.enums.AlertStatus;
import com.sogeco.fleet.common.event.AlertTriggeredEvent;
import com.sogeco.fleet.modules.alert.Alert;
import com.sogeco.fleet.modules.alert.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envoie une notification push pour chaque NOUVELLE alerte. L'evenement est aussi publie a la
 * resolution ou a la cloture automatique : on l'ignore alors (statut non ouvert). Une alerte
 * qui se repete n'emet pas d'evenement (AlertService l'absorbe), donc pas de rafale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushAlertListener {

    private final AlertRepository alertRepository;
    private final PushNotificationService push;

    @Async
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlert(AlertTriggeredEvent event) {
        if (!push.enabled()) {
            return;
        }
        try {
            Alert alert = alertRepository.findById(event.alertId()).orElse(null);
            if (alert == null || alert.getStatus() != AlertStatus.NON_RESOLUE
                    || alert.getOccurrences() == null || alert.getOccurrences() > 1) {
                return;
            }
            Long cityId = alert.getVehicle() == null || alert.getVehicle().getCity() == null
                    ? null : alert.getVehicle().getCity().getId();
            String plate = alert.getVehicle() == null ? "" : alert.getVehicle().getRegistrationNumber() + " — ";
            String text = alert.getDescription() == null ? "" : alert.getDescription();
            if (text.length() > 180) {
                text = text.substring(0, 177) + "...";
            }
            push.notifyAlert(cityId, "[" + alert.getLevel() + "] " + alert.getTitle(), plate + text, "/alertes", alert.getId());
        } catch (RuntimeException e) {
            log.warn("Push d'alerte impossible", e);
        }
    }
}
