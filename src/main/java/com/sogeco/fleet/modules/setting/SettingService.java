package com.sogeco.fleet.modules.setting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Acces typé aux parametres systeme.
 *
 * Toute lecture fournit une valeur de repli : un parametre supprime ou
 * mal saisi ne doit jamais empecher l'application de fonctionner.
 */
@Service
@RequiredArgsConstructor
public class SettingService {

    public static final String MAX_FAILED_ATTEMPTS   = "security.max_failed_attempts";
    public static final String LOCK_DURATION_MINUTES = "security.lock_duration_minutes";
    public static final String TOTP_REQUIRED_ROLES   = "security.totp_required_roles";

    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        return repository.findBySettingKey(key)
                .map(setting -> setting.asInt(defaultValue))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return repository.findBySettingKey(key)
                .map(setting -> setting.asDecimal(defaultValue))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String key, boolean defaultValue) {
        return repository.findBySettingKey(key)
                .map(setting -> setting.asBoolean(defaultValue))
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        return repository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElse(defaultValue);
    }

    /** Liste issue d'une valeur separee par des virgules. */
    @Transactional(readOnly = true)
    public List<String> getList(String key) {
        String value = getString(key, "");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
