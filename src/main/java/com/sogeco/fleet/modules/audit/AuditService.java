package com.sogeco.fleet.modules.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ecriture du journal d'audit.
 *
 * Chaque trace est ecrite dans une transaction independante
 * (REQUIRES_NEW) : un echec metier annule l'operation, mais la trace
 * de la tentative doit subsister. Une erreur d'ecriture d'audit ne
 * remonte jamais a l'appelant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userEmail, String action, String entityType, Long entityId, String ipAddress) {
        record(userEmail, action, entityType, entityId, ipAddress, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String userEmail, String action, String entityType, Long entityId,
                       String ipAddress, String oldValue, String newValue) {
        try {
            repository.save(AuditLog.builder()
                    .userEmail(userEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .ipAddress(ipAddress)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build());
        } catch (RuntimeException e) {
            log.error("Echec d'ecriture du journal d'audit [{}] pour {}", action, userEmail, e);
        }
    }
}
