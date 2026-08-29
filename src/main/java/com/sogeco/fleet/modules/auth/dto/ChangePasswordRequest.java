package com.sogeco.fleet.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Changement du mot de passe par son titulaire, y compris un administrateur")
public record ChangePasswordRequest(

        @NotBlank(message = "le mot de passe actuel est obligatoire")
        String currentPassword,

        @NotBlank(message = "le nouveau mot de passe est obligatoire")
        @Size(min = 5, message = "le nouveau mot de passe doit comporter au moins 5 caracteres")
        String newPassword
) {
}
