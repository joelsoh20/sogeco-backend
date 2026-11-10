package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Etat « consultee » des alertes, propre a chaque utilisateur. Le compteur de la cloche
 * ne compte que les alertes ouvertes que l'utilisateur n'a pas encore consultees, dans
 * son perimetre de ville (RG-13.4). Une alerte qui se repete redevient non lue.
 */
@Service
@RequiredArgsConstructor
public class AlertReadService {

    private static final String OPEN = "('NON_RESOLUE','EN_COURS')";

    private final EntityManager em;
    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public long unreadCount() {
        Long userId = SecurityUtils.currentUserId().orElseThrow();
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        Number n = (Number) em.createNativeQuery("""
                SELECT COUNT(*) FROM alerts a
                LEFT JOIN vehicles v ON v.id = a.vehicle_id
                WHERE a.status IN %s
                  AND (CAST(:cityId AS BIGINT) IS NULL OR v.city_id = :cityId)
                  AND NOT EXISTS (SELECT 1 FROM alert_reads r
                                  WHERE r.alert_id = a.id AND r.user_id = :userId
                                    AND r.occurrences >= a.occurrences)
                """.formatted(OPEN))
                .setParameter("cityId", cityId).setParameter("userId", userId)
                .getSingleResult();
        return n.longValue();
    }

    @Transactional
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public void markRead(Long alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte", alertId));
        Long userId = SecurityUtils.currentUserId().orElseThrow();
        em.createNativeQuery("""
                INSERT INTO alert_reads (alert_id, user_id, occurrences) VALUES (:alertId, :userId, :occ)
                ON CONFLICT (alert_id, user_id) DO UPDATE SET occurrences = EXCLUDED.occurrences, read_at = now()
                """)
                .setParameter("alertId", alertId).setParameter("userId", userId)
                .setParameter("occ", alert.getOccurrences())
                .executeUpdate();
    }

    @Transactional
    @PreAuthorize("hasAuthority('ALERT_READ')")
    public void markAllRead() {
        Long userId = SecurityUtils.currentUserId().orElseThrow();
        Long cityId = SecurityUtils.currentCityId().orElse(null);
        em.createNativeQuery("""
                INSERT INTO alert_reads (alert_id, user_id, occurrences)
                SELECT a.id, :userId, a.occurrences FROM alerts a
                LEFT JOIN vehicles v ON v.id = a.vehicle_id
                WHERE a.status IN %s
                  AND (CAST(:cityId AS BIGINT) IS NULL OR v.city_id = :cityId)
                ON CONFLICT (alert_id, user_id) DO UPDATE SET occurrences = EXCLUDED.occurrences, read_at = now()
                """.formatted(OPEN))
                .setParameter("cityId", cityId).setParameter("userId", userId)
                .executeUpdate();
    }
}
