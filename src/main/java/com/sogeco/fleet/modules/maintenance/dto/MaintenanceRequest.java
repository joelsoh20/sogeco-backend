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

        @PositiveOrZero(message = "la quantite de carburant ne peut pas etre negative")
        @Schema(description = "Carburant consomme au garage pour les essais du vehicule pendant cette intervention, facultatif")
        BigDecimal testFuelLiters,

        @Schema(description = "Vrai pour une panne subie, faux pour un entretien planifie")
        Boolean isBreakdown,

        @Size(max = 30)
        @Schema(description = "Code defaut moteur, si remonte par le boitier")
        String errorCode,

        LocalDate nextInterventionDate,

        @PositiveOrZero
        BigDecimal nextInterventionKm,

        @PositiveOrZero(message = "le temps passe en panne ne peut pas etre negatif")
        @Schema(description = "Temps passe en panne, en jours : saisi pour une reparation, repris dans le rapport du camion")
        Integer downtimeDays,

        @PositiveOrZero(message = "la somme depensee ne peut pas etre negative")
        @Schema(description = "Somme totale depensee (FCFA), alternative au detail des lignes")
        BigDecimal amount,

        Long invoiceDocumentId,

        @Valid
        List<MaintenanceItemRequest> items
) {
}
