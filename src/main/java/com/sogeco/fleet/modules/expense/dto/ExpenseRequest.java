package com.sogeco.fleet.modules.expense.dto;

import com.sogeco.fleet.common.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Depense hors carburant et maintenance")
public record ExpenseRequest(

        @NotNull(message = "la date est obligatoire")
        LocalDate expenseDate,

        @NotNull(message = "la categorie est obligatoire")
        ExpenseCategory category,

        @NotBlank(message = "le libelle est obligatoire")
        @Size(max = 200)
        @Schema(example = "Peage de Douala")
        String label,

        @NotNull(message = "le montant est obligatoire")
        @PositiveOrZero
        BigDecimal amount,

        Long vehicleId,
        Long driverId,

        @Schema(description = "Une depense rattachee a une mission entre dans son cout direct")
        Long missionId,

        Long agencyId,
        Long partnerId,
        Long documentId,

        @Size(max = 500)
        String notes
) {
}
