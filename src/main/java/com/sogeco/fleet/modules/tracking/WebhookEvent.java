package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.WebhookStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Trame brute recue, enregistree AVANT tout traitement.
 *
 * C'est ce qui permet de diagnostiquer une integration en production :
 * une trame rejetee laisse une trace exploitable, avec son contenu
 * exact et le motif du rejet.
 */
@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", nullable = false, length = 40)
    private String provider;

    @Column(name = "device_id", length = 60)
    private String deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Builder.Default
    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid = Boolean.FALSE;

    @Builder.Default
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookStatus status = WebhookStatus.RECU;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public void markProcessed() {
        this.status = WebhookStatus.TRAITE;
        this.processedAt = Instant.now();
    }

    public void markRejected(WebhookStatus reason, String message) {
        this.status = reason;
        this.processedAt = Instant.now();
        this.errorMessage = message;
    }
}
