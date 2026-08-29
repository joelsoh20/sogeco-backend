package com.sogeco.fleet.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Elements d'activation de la double authentification")
public record TotpSetupResponse(

        @Schema(description = "Secret partage, a saisir manuellement si le QR code echoue")
        String secret,

        @Schema(description = "URI otpauth a encoder en QR code cote frontend")
        String otpAuthUri
) {
}
