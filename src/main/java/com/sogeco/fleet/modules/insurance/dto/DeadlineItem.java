package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.DocumentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Ligne de l'echeancier unifie.
 *
 * Combine trois sources qui ne partagent aucune table commune :
 * documents generiques, polices d'assurance, visites techniques,
 * plus l'echeance de permis portee directement par le chauffeur.
 * C'est cette vue qui repond au besoin d'un echeancier unique, sans
 * fusionner des modeles de donnees qui n'ont pas vocation a l'etre.
 */
@Schema(description = "Une echeance, quelle que soit sa source")
public record DeadlineItem(
        String category,
        Long entityId,
        String entityLabel,
        String documentLabel,
        LocalDate dueDate,
        Long daysRemaining,
        DocumentStatus status
) {
}
