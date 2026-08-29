package com.sogeco.fleet.modules.insurance.dto;

import com.sogeco.fleet.common.enums.BodyType;
import com.sogeco.fleet.common.enums.InsuranceCoverageType;
import com.sogeco.fleet.common.enums.PaymentFrequency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

@Schema(description = "Contrat d'assurance, pouvant couvrir plusieurs camions")
public record InsurancePolicyRequest(

        @NotBlank(message = "le numero de police est obligatoire")
        @Size(max = 50)
        @Schema(example = "ASS-2026-04471")
        String policyNumber,

        @NotNull(message = "l'assureur est obligatoire")
        Long partnerId,

        @NotNull(message = "le type de couverture est obligatoire")
        InsuranceCoverageType coverageType,

        @Schema(description = "Categorie du vehicule assure")
        BodyType category,

        @Schema(description = "Immatriculation du vehicule assure ('Genre' sur le formulaire), recopiee depuis l'attestation")
        @Size(max = 20)
        String vehicleRegistration,

        @PositiveOrZero
        BigDecimal premiumAmount,

        PaymentFrequency paymentFrequency,

        @NotNull(message = "la date de debut est obligatoire")
        LocalDate startDate,

        @NotNull(message = "la date de fin est obligatoire")
        LocalDate endDate,

        Set<Long> vehicleIds,

        @Size(max = 500)
        String notes
) {
}
