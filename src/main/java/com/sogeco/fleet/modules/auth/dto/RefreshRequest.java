package com.sogeco.fleet.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "le jeton de rafraichissement est obligatoire")
        String refreshToken
) {
}
