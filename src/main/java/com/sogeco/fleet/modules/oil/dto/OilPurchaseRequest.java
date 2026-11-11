package com.sogeco.fleet.modules.oil.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OilPurchaseRequest(
        @NotNull(message = "la date d'achat est obligatoire")
        @PastOrPresent(message = "la date d'achat ne peut pas etre dans le futur")
        LocalDate purchaseDate,

        @NotNull(message = "la quantite est obligatoire")
        @Positive(message = "la quantite doit etre superieure a zero")
        @DecimalMax(value = "100000", message = "quantite invraisemblable")
        BigDecimal quantityLiters,

        @Size(max = 200)
        String note,

        /** Ville du stock : imposee a un gestionnaire (sa ville), a choisir par l'administrateur. */
        Long cityId
) {
}
