package com.sogeco.fleet.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Mot de passe choisi par l'administrateur pour un tiers -- "
        + "distinct de la reinitialisation (mot de passe genere aleatoirement)")
public record SetUserPasswordRequest(

        @NotBlank(message = "le nouveau mot de passe est obligatoire")
        @Size(min = 5, message = "le nouveau mot de passe doit comporter au moins 5 caracteres")
        String newPassword
) {
}
