package com.sogeco.fleet.common.enums;

/**
 * Types d'alerte du moteur de regles.
 *
 * Les seuils associes vivent dans alert_rules, jamais dans le code :
 * SOGECO doit pouvoir les ajuster sans redeploiement (RG-10.3).
 */
public enum AlertType {

    // Telematique temps reel
    VITESSE_EXCESSIVE,
    TEMPERATURE_MOTEUR,
    PANNE_DETECTEE,
    CARBURANT_BAS,
    SIPHONNAGE,
    DEVIATION_ITINERAIRE,
    GEOREPERAGE,
    DEMARRAGE_NON_AUTORISE,
    PERTE_SIGNAL,

    // Analyse differee
    SURCONSOMMATION,
    MAINTENANCE_ECHUE,

    // Echeances documentaires
    ASSURANCE_ECHEANCE,
    VISITE_TECHNIQUE_ECHEANCE,
    PERMIS_ECHEANCE,

    // Divers
    PEAGE_IMPAYE,
    MISSION_SANS_CA;

    /** Alertes evaluees a chaque trame recue, par opposition aux taches planifiees. */
    public boolean isRealTime() {
        return switch (this) {
            case VITESSE_EXCESSIVE, TEMPERATURE_MOTEUR, PANNE_DETECTEE, CARBURANT_BAS,
                 SIPHONNAGE, DEVIATION_ITINERAIRE, GEOREPERAGE, DEMARRAGE_NON_AUTORISE -> true;
            default -> false;
        };
    }
}
