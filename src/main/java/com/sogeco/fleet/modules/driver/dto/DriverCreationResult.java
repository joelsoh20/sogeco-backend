package com.sogeco.fleet.modules.driver.dto;

/**
 * Reponse a la creation d'un chauffeur.
 *
 * temporaryPassword n'est present que si un nouveau compte de connexion
 * a ete cree via accountEmail : le mot de passe genere n'est renvoye
 * qu'une seule fois, jamais stocke en clair, ni recuperable ensuite.
 */
public record DriverCreationResult(
        DriverDetailResponse driver,
        String temporaryPassword
) {
}
