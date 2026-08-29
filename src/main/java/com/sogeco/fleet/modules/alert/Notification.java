package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.enums.NotificationChannel;
import com.sogeco.fleet.common.enums.NotificationStatus;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Notification destinee a un utilisateur, liee ou non a une alerte. */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id")
    private Alert alert;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", length = 500)
    private String message;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.EN_ATTENTE;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public void markRead() {
        this.readAt = Instant.now();
    }
}
