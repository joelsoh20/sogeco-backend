package com.sogeco.fleet.modules.insurance.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CarteBleueRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotBlank(message = "le numero de recu est obligatoire")
        @Size(max = 50)
        String receiptNumber,

        @Size(max = 150)
        String category,

        @NotNull(message = "la date de delivrance est obligatoire")
        LocalDate issueDate,

        @NotNull(message = "la date d'expiration est obligatoire")
        LocalDate expiryDate,

        @PositiveOrZero
        BigDecimal power,

        @PositiveOrZero
        BigDecimal cost,

        @Size(max = 500)
        String notes
) {
}
