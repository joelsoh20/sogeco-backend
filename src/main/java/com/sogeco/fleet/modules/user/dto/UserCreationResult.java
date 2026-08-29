package com.sogeco.fleet.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reponse a la creation d'un utilisateur.
 *
 * temporaryPassword n'est present QUE si l'administrateur n'a pas saisi
 * de mot de passe a la creation : le backend en a genere un et doit le
 * communiquer une seule fois, exactement comme resetPassword(). Il
 * n'est jamais stocke en clair ni recuperable ensuite — si l'ecran est
 * ferme sans l'avoir note, seule une reinitialisation le recupere.
 */
@Schema(description = "Utilisateur cree. temporaryPassword absent si un mot de passe a ete saisi explicitement.")
public record UserCreationResult(
        UserResponse user,

        @Schema(description = "A transmettre au nouvel utilisateur par un canal sur. Non conserve en clair.")
        String temporaryPassword
) {
}
