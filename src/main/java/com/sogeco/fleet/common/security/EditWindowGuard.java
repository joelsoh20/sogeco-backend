package com.sogeco.fleet.common.security;

import com.sogeco.fleet.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Fenetre de modification libre apres la creation d'un enregistrement.
 *
 * Passe ce delai, seul un administrateur peut encore corriger les
 * informations saisies (Carburant, Maintenance, Camions, Missions,
 * Chauffeurs) : une correction tardive et non tracee remettrait en
 * cause la fiabilite des rapports deja calcules sur ces donnees.
 *
 * Le meme mecanisme existait deja, duplique en ligne, dans
 * FuelService.update() (RG-6.10) — centralise ici pour les cinq
 * modules plutot que reecrit cinq fois.
 */
public final class EditWindowGuard {

    private EditWindowGuard() {
    }

    /** Vrai si l'enregistrement est encore modifiable : administrateur, ou dans le delai. */
    public static boolean isWithinWindow(Instant createdAt, int windowHours) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        return createdAt == null || Duration.between(createdAt, Instant.now()).toHours() <= windowHours;
    }

    /** Leve une exception si l'enregistrement n'est plus modifiable par l'utilisateur courant. */
    public static void assertEditable(Instant createdAt, int windowHours, String ruleCode, String entityLabel) {
        if (isWithinWindow(createdAt, windowHours)) {
            return;
        }
        throw new BusinessException(ruleCode,
                "%s enregistre(e) il y a plus de %d heure(s) : seul un administrateur peut encore le/la modifier"
                        .formatted(entityLabel, windowHours),
                HttpStatus.FORBIDDEN);
    }
}
