package com.sogeco.fleet.modules.insurance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pas de vehicleId : la licence de transport couvre toute la flotte. */
public record TransportLicenseRequest(

        @NotBlank(message = "la reference est obligatoire")
        @Size(max = 50)
        String reference,

        @Size(max = 150)
        String issuingAuthority,

        @Size(max = 50)
        String receiptNumber,

        @Size(max = 30)
        String power,

        @NotNull(message = "la date de delivrance est obligatoire")
        LocalDate issueDate,

        @NotNull(message = "la date d'expiration est obligatoire")
        LocalDate expiryDate,

        @PositiveOrZero
        BigDecimal cost,

        @Size(max = 500)
        String notes
) {
}
