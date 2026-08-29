package com.sogeco.fleet.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** L'adresse de connexion n'est pas modifiable : elle identifie le compte. */
public record UserUpdateRequest(

        @NotBlank(message = "le prenom est obligatoire")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 80)
        String lastName,

        @Size(max = 30)
        String phone,

        Long cityId,

        @NotEmpty(message = "au moins un role est obligatoire")
        Set<String> roleCodes
) {
}
