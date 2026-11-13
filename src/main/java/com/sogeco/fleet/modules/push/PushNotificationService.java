package com.sogeco.fleet.modules.push;

import com.sogeco.fleet.common.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.List;
import java.util.Map;

/**
 * Notifications push (Web Push, cles VAPID). Elles arrivent meme navigateur ferme, sur PC
 * comme sur telephone (PWA installee). Sans cles configurees, le push est desactive et le
 * reste de l'application n'est pas affecte.
 */
@Slf4j
@Service
public class PushNotificationService {

    private final EntityManager em;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private PushService pushService;

    public PushNotificationService(EntityManager em,
                                   @Value("${sogeco.push.public-key:}") String publicKey,
                                   @Value("${sogeco.push.private-key:}") String privateKey,
                                   @Value("${sogeco.push.subject:mailto:admin@sogeco.cm}") String subject) {
        this.em = em;
        this.publicKey = publicKey == null ? "" : publicKey.trim();
        this.privateKey = privateKey == null ? "" : privateKey.trim();
        this.subject = subject;
    }

    @PostConstruct
    void init() {
        if (!enabled()) {
            log.warn("Push desactive : sogeco.push.public-key / private-key non renseignees");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushService(publicKey, privateKey, subject);
        } catch (Exception e) {
            log.error("Cles VAPID invalides, push desactive", e);
            pushService = null;
        }
    }

    public boolean enabled() {
        return !publicKey.isEmpty() && !privateKey.isEmpty();
    }

    public String publicKey() {
        return publicKey;
    }

    // ------------------------------------------------------------------
    // Abonnements
    // ------------------------------------------------------------------

    @Transactional
    public void subscribe(String endpoint, String p256dh, String auth, String userAgent) {
        Long userId = SecurityUtils.currentUserId().orElseThrow();
        // Un meme appareil change de compte : l'abonnement suit le dernier utilisateur connecte.
        em.createNativeQuery("""
                INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth, user_agent)
                VALUES (:userId, :endpoint, :p256dh, :auth, :ua)
                ON CONFLICT (endpoint) DO UPDATE
                SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh,
                    auth = EXCLUDED.auth, user_agent = EXCLUDED.user_agent
                """)
                .setParameter("userId", userId).setParameter("endpoint", endpoint)
                .setParameter("p256dh", p256dh).setParameter("auth", auth)
                .setParameter("ua", userAgent == null ? null : userAgent.substring(0, Math.min(250, userAgent.length())))
                .executeUpdate();
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        Long userId = SecurityUtils.currentUserId().orElseThrow();
        em.createNativeQuery("DELETE FROM push_subscriptions WHERE endpoint = :endpoint AND user_id = :userId")
                .setParameter("endpoint", endpoint).setParameter("userId", userId)
                .executeUpdate();
    }

    // ------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------

    /**
     * Previent les utilisateurs actifs qui peuvent lire les alertes et dont le perimetre de
     * ville couvre l'alerte (RG-13.4) : un gestionnaire de Douala ne recoit pas Yaounde.
     * Sans ville (alerte sans vehicule), seuls les comptes sans perimetre sont prevenus.
     */
    @Transactional
    public void notifyAlert(Long cityId, String title, String body, String url, Long alertId) {
        if (pushService == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object[]> targets = em.createNativeQuery("""
                SELECT s.id, s.endpoint, s.p256dh, s.auth
                FROM push_subscriptions s
                JOIN users u ON u.id = s.user_id
                WHERE u.status = 'ACTIF' AND u.deleted_at IS NULL
                  AND (u.city_id IS NULL OR u.city_id = CAST(:cityId AS BIGINT))
                  AND EXISTS (SELECT 1 FROM user_roles ur
                              JOIN role_permissions rp ON rp.role_id = ur.role_id
                              JOIN permissions p ON p.id = rp.permission_id
                              WHERE ur.user_id = u.id AND p.code = 'ALERT_READ')
                """).setParameter("cityId", cityId).getResultList();
        if (targets.isEmpty()) {
            return;
        }

        String payload = toJson(Map.of(
                "title", title, "body", body, "url", url,
                "tag", "alert-" + alertId));

        for (Object[] t : targets) {
            Long id = ((Number) t[0]).longValue();
            try {
                var response = pushService.send(new Notification((String) t[1], (String) t[2], (String) t[3], payload));
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    // Appareil desinstalle ou autorisation retiree : on oublie l'abonnement.
                    em.createNativeQuery("DELETE FROM push_subscriptions WHERE id = :id")
                            .setParameter("id", id).executeUpdate();
                } else if (status >= 300) {
                    log.warn("Push refuse (HTTP {}) pour l'abonnement {}", status, id);
                }
            } catch (Exception e) {
                log.warn("Push impossible pour l'abonnement {} : {}", id, e.toString());
            }
        }
    }

    private static String toJson(Map<String, String> values) {
        StringBuilder sb = new StringBuilder("{");
        values.forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(',');
            }
            sb.append('"').append(k).append("\":\"").append(escape(v)).append('"');
        });
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
