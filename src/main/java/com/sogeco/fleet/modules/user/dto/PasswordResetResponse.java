package com.sogeco.fleet.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mot de passe temporaire, affiche une seule fois")
public record PasswordResetResponse(
        @Schema(description = "A transmettre a l'utilisateur par un canal sur. Non conserve en clair.")
        String temporaryPassword
) {
}
