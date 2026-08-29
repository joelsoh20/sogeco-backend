package com.sogeco.fleet.modules.audit.dto;

import com.sogeco.fleet.modules.audit.AuditLog;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String userEmail,
        String action,
        String entityType,
        Long entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getUserEmail(), log.getAction(), log.getEntityType(),
                log.getEntityId(), log.getOldValue(), log.getNewValue(),
                log.getIpAddress(), log.getCreatedAt());
    }
}
