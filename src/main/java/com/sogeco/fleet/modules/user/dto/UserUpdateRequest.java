package com.sogeco.fleet.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserUpdateRequest(

        @NotBlank(message = "le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Email(message = "format d'adresse invalide")
        @Size(max = 150)
        @Schema(description = "Facultative : absente ou vide, l'adresse actuelle (reelle ou "
                + "generee a la creation) n'est pas modifiee. Fournie, elle la remplace — "
                + "c'est ainsi qu'un compte cree sans email reel peut en recevoir un.")
        String email,

        @Size(max = 30)
        String phone,

        Long cityId,

        @NotEmpty(message = "au moins un role est obligatoire")
        Set<String> roleCodes
) {
}
