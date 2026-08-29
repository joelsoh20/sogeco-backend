package com.sogeco.fleet.modules.document.dto;

import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Metadonnees accompagnant le televersement")
public record DocumentUploadRequest(

        @NotNull(message = "le type de porteur est obligatoire")
        EntityType entityType,

        @NotNull(message = "l'identifiant du porteur est obligatoire")
        Long entityId,

        @NotNull(message = "le type de document est obligatoire")
        DocumentType documentType,

        String referenceNumber,
        LocalDate issueDate,

        @Schema(description = "Obligatoire pour les documents a echeance")
        LocalDate expiryDate,

        String notes
) {
}
