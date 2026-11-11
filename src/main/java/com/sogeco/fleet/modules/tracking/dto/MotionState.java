package com.sogeco.fleet.modules.tracking.dto;

import java.time.Instant;

/**
 * Etat de deplacement d'un camion et depuis quand il dure.
 *
 * since est le debut de la serie ininterrompue de releves dans cet etat (le premier releve
 * apres le dernier releve de l'etat inverse). openEnded est vrai quand l'historique conserve
 * pour le calcul (7 jours) ne contient aucun releve de l'etat inverse : l'etat dure alors
 * depuis AU MOINS since.
 *
 * Pour un camion hors ligne, since est l'heure de la derniere trame recue.
 */
public record MotionState(
        Long vehicleId,
        String registrationNumber,
        Motion state,
        Instant since,
        boolean openEnded,
        Instant lastSeen
) {

    public enum Motion {
        /** Vitesse au-dessus du seuil d'arret parametre. */
        EN_MOUVEMENT,
        /** A l'arret, moteur allume (ou contact inconnu). */
        STATIONNE,
        /** A l'arret, moteur eteint. */
        GARE,
        /** Aucune trame depuis le delai parametre. */
        HORS_LIGNE,
        /** Camion sans boitier GPS : aucun releve mesure. */
        INCONNU
    }
}
