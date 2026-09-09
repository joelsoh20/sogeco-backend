package com.sogeco.fleet.modules.insurance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Carte rose CEMAC (attestation d'assurance automobile)")
public record CarteRoseRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotNull(message = "la societe d'assurance est obligatoire")
        Long insurerId,

        @NotBlank(message = "le nom de l'assure est obligatoire")
        @Size(max = 150)
        @Schema(example = "SOGECO SARL")
        String insuredName,

        @Size(max = 255)
        String insuredAddress,

        @NotBlank(message = "le nom du bureau emetteur est obligatoire")
        @Size(max = 150)
        @Schema(example = "Bureau Direct Bafoussam")
        String issuingBureauName,

        @Size(max = 255)
        String issuingBureauAddress,

        @NotBlank(message = "le numero d'immatriculation est obligatoire")
        @Size(max = 20)
        String registrationNumber,

        @NotBlank(message = "la marque et le type du vehicule sont obligatoires")
        @Size(max = 100)
        String vehicleMakeType,

        @NotBlank(message = "la categorie du vehicule est obligatoire")
        @Size(max = 60)
        String vehicleCategory,

        @NotNull(message = "la date de debut de validite est obligatoire")
        LocalDate validFrom,

        @Schema(description = "Si absente, calculee au debut de validite + duree par defaut (1 an)")
        LocalDate validTo,

        @PositiveOrZero
        BigDecimal cost,

        @Size(max = 500)
        String notes
) {
}
