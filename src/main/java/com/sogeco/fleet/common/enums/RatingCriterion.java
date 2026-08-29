package com.sogeco.fleet.common.enums;

/**
 * Les cinq criteres de notation des chauffeurs (RG-9.5).
 *
 * Quatre sont calcules automatiquement. RESPECT_REGLES est le seul sans
 * source de donnees : il doit etre saisi par un responsable (RG-9.6).
 */
public enum RatingCriterion {

    CONDUITE_SECURISEE("performance.weight_safety", true),
    CONSOMMATION_ECONOMIQUE("performance.weight_fuel", true),
    RESPECT_DELAIS("performance.weight_punctuality", true),
    ENTRETIEN_VEHICULE("performance.weight_vehicle_care", true),
    RESPECT_REGLES("performance.weight_compliance", false);

    private final String weightSettingKey;
    private final boolean automatic;

    RatingCriterion(String weightSettingKey, boolean automatic) {
        this.weightSettingKey = weightSettingKey;
        this.automatic = automatic;
    }

    public String getWeightSettingKey() {
        return weightSettingKey;
    }

    public boolean isAutomatic() {
        return automatic;
    }
}
