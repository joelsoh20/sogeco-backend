package com.sogeco.fleet.modules.document.dto;

import com.sogeco.fleet.common.enums.DocumentStatus;
import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.modules.document.Document;

import java.time.LocalDate;

public record DocumentResponse(
        Long id,
        EntityType entityType,
        Long entityId,
        DocumentType documentType,
        String fileName,
        String mimeType,
        Long fileSize,
        String referenceNumber,
        LocalDate issueDate,
        LocalDate expiryDate,
        Long daysRemaining,
        DocumentStatus status,
        String notes
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getEntityType(),
                document.getEntityId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getMimeType(),
                document.getFileSize(),
                document.getReferenceNumber(),
                document.getIssueDate(),
                document.getExpiryDate(),
                document.daysRemaining(),
                document.getStatus(),
                document.getNotes());
    }
}
