package com.sogeco.fleet.modules.driver.dto;

import com.sogeco.fleet.common.enums.RatingCriterion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Saisie d'une note. Reservee au critere Respect des regles, les autres etant calcules.")
public record RatingRequest(

        @NotNull(message = "le critere est obligatoire")
        RatingCriterion criterion,

        @NotNull(message = "la note est obligatoire")
        @DecimalMin(value = "0",   message = "la note ne peut pas etre negative")
        @DecimalMax(value = "100", message = "la note ne peut pas depasser 100")
        BigDecimal score100,

        @Schema(description = "Mois evalue. Par defaut, le mois en cours.")
        LocalDate periodMonth,

        @Size(max = 500)
        String comment
) {
}
