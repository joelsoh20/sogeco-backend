package com.sogeco.fleet.modules.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(

        @NotBlank(message = "le code est obligatoire")
        @Pattern(regexp = "ROLE_[A-Z_]{3,34}", message = "le code doit suivre le format ROLE_XXX en majuscules")
        String code,

        @NotBlank(message = "le libelle est obligatoire")
        @Size(max = 80)
        String label,

        @Size(max = 255)
        String description
) {
}
