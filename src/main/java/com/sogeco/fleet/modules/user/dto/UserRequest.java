package com.sogeco.fleet.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Set;

@Schema(description = "Creation d'un utilisateur")
public record UserRequest(

        @Email(message = "format d'adresse invalide")
        @Size(max = 150)
        @Schema(description = "Facultative : sans email, le compte se connecte par nom et prenom")
        String email,

        @NotBlank(message = "le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Size(max = 30)
        String phone,

        @Schema(description = "Ville geree par ce compte (filtre son acces aux camions/chauffeurs/missions). Absent pour un administrateur, qui voit tout.")
        Long cityId,

        @NotEmpty(message = "au moins un role est obligatoire")
        @Schema(example = "[\"ROLE_GESTIONNAIRE\"]")
        Set<String> roleCodes,

        @Size(min = 5, message = "le mot de passe doit comporter au moins 5 caracteres")
        @Schema(description = "Mot de passe initial. Si absent, un mot de passe aleatoire est genere.")
        String password
) {
}
