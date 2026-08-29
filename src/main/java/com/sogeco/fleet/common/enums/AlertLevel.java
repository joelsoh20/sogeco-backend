package com.sogeco.fleet.common.enums;

/** Niveaux de gravite, alignes sur l'ecran Alertes et Centre de Controle. */
public enum AlertLevel {
    CRITIQUE,
    IMPORTANT,
    MINEUR,
    INFORMATION;

    public boolean requiresImmediateAction() {
        return this == CRITIQUE;
    }
}
