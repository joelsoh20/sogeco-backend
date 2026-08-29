package com.sogeco.fleet.modules.setting;

import com.sogeco.fleet.common.entity.BaseEntity;
import com.sogeco.fleet.common.enums.SettingValueType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Parametre systeme. Aucun seuil metier n'est code en dur (RG-13.1) :
 * vitesse maximale, ponderations de notation, delais d'echeance,
 * retention GPS, tout passe par cette table.
 */
@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, length = 100, unique = true)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "text")
    private String settingValue;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private SettingValueType valueType = SettingValueType.STRING;

    @Column(name = "category", nullable = false, length = 40)
    private String category;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    // ------------------------------------------------------------------
    // Conversions typees, avec valeur de repli si le parametre est absent
    // ou mal saisi : un seuil corrompu ne doit jamais bloquer le demarrage.
    // ------------------------------------------------------------------

    public int asInt(int defaultValue) {
        try {
            return Integer.parseInt(settingValue.trim());
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public BigDecimal asDecimal(BigDecimal defaultValue) {
        try {
            return new BigDecimal(settingValue.trim());
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    public boolean asBoolean(boolean defaultValue) {
        if (settingValue == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(settingValue.trim());
    }
}
