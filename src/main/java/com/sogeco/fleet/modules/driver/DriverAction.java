package com.sogeco.fleet.modules.driver;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.DriverActionType;
import com.sogeco.fleet.modules.document.Document;
import com.sogeco.fleet.modules.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Action RH tracee : prime, avertissement, formation, entretien.
 * Correspond aux trois boutons de la fiche chauffeur (RG-9.12).
 */
@Entity
@Table(name = "driver_actions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private DriverActionType actionType;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    @Column(name = "motif", nullable = false, length = 255)
    private String motif;

    @Column(name = "comment", length = 1000)
    private String comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private Document document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;
}
