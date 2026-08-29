package com.sogeco.fleet.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpVerifyRequest(
        @NotBlank(message = "le code est obligatoire")
        @Pattern(regexp = "\\d{6}", message = "le code doit comporter 6 chiffres")
        String code
) {
}
