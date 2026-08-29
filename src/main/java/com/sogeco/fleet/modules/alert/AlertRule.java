package com.sogeco.fleet.modules.alert;

import com.sogeco.fleet.common.entity.SoftDeletableEntity;
import com.sogeco.fleet.common.enums.AlertLevel;
import com.sogeco.fleet.common.enums.AlertType;
import com.sogeco.fleet.common.enums.ComparisonOperator;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Regle de declenchement, entierement parametrable.
 *
 * Aucun seuil n'est code en dur : la vitesse maximale, la temperature
 * moteur ou le niveau de carburant bas s'ajustent depuis l'interface,
 * sans redeploiement (RG-10.3).
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule extends SoftDeletableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 40, unique = true)
    private AlertType alertType;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "threshold_value", precision = 12, scale = 2)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", length = 10)
    private ComparisonOperator comparisonOperator;

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false, length = 20)
    private AlertLevel level;

    /** Fenetre anti-repetition, en minutes (RG-10.4). */
    @Builder.Default
    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes = 30;

    @Column(name = "notify_role_codes", length = 255)
    private String notifyRoleCodes;

    // ------------------------------------------------------------------

    /** La regle se declenche-t-elle pour cette valeur ? */
    public boolean matches(BigDecimal value) {
        if (comparisonOperator == null || thresholdValue == null) {
            // Regle sans seuil : le declenchement est decide par
            // l'evaluateur lui-meme, par exemple un code defaut present.
            return true;
        }
        return comparisonOperator.test(value, thresholdValue);
    }

    public List<String> notifiedRoles() {
        if (notifyRoleCodes == null || notifyRoleCodes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(notifyRoleCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
