package com.sogeco.fleet.modules.maintenance.dto;

import com.sogeco.fleet.common.enums.MaintenanceCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Intervention de maintenance")
public record MaintenanceRequest(

        @NotNull(message = "le camion est obligatoire")
        Long vehicleId,

        @Schema(description = "Garage prestataire")
        Long partnerId,

        @NotNull(message = "la categorie est obligatoire")
        MaintenanceCategory category,

        @NotBlank(message = "la description est obligatoire")
        @Size(max = 500)
        @Schema(example = "Vidange + filtres")
        String description,

        @NotNull(message = "la date d'intervention est obligatoire")
        LocalDate interventionDate,

        LocalDate completionDate,

        @PositiveOrZero
        BigDecimal odometerKm,

        @Schema(description = "Vrai pour une panne subie, faux pour un entretien planifie")
        Boolean isBreakdown,

        @Size(max = 30)
        @Schema(description = "Code defaut moteur, si remonte par le boitier")
        String errorCode,

        LocalDate nextInterventionDate,

        @PositiveOrZero
        BigDecimal nextInterventionKm,

        Long invoiceDocumentId,

        @Valid
        List<MaintenanceItemRequest> items
) {
}
