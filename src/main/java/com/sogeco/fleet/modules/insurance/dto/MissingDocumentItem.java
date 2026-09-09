package com.sogeco.fleet.modules.insurance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Signale un camion actif n'ayant JAMAIS eu de document d'un type donne
 * — distinct de l'echeancier (DeadlineItem), qui ne peut lister que des
 * documents deja saisis en train d'arriver a echeance. Un camion sans
 * aucune carte rose, par exemple, n'apparaitrait jamais dans
 * l'echeancier faute d'une date a suivre.
 */
@Schema(description = "Document jamais enregistre pour un camion actif")
public record MissingDocumentItem(
        String category,
        Long vehicleId,
        String registrationNumber
) {
}
