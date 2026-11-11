package com.sogeco.fleet.modules.oil.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OilDistributionRequest(
        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @NotNull(message = "la date est obligatoire")
        @PastOrPresent(message = "la date ne peut pas etre dans le futur")
        LocalDate distributionDate,

        @NotNull(message = "la quantite est obligatoire")
        @Positive(message = "la quantite doit etre superieure a zero")
        @DecimalMax(value = "10000", message = "quantite invraisemblable")
        BigDecimal quantityLiters,

        @Size(max = 150)
        String recipientName,

        @Size(max = 200)
        String note
) {
}
