package com.sogeco.fleet.modules.client.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Fiche client")
public record ClientRequest(

        @Size(max = 20)
        @Schema(description = "Genere automatiquement depuis la raison sociale si absent", example = "SOCPALM")
        String code,

        @NotBlank(message = "la raison sociale est obligatoire")
        @Size(max = 150)
        @Schema(example = "SOCPALM SA")
        String companyName,

        @Size(max = 120)
        String contactName,

        @Size(max = 30)
        String phone,

        @Email(message = "format d'adresse invalide")
        @Size(max = 150)
        String email,

        @Size(max = 255)
        String address,

        Long cityId,

        @Size(max = 50)
        String taxNumber,

        @PositiveOrZero(message = "le delai de paiement ne peut pas etre negatif")
        Integer paymentTermsDays,

        @Size(max = 500)
        String notes
) {
}
