package com.sogeco.fleet.modules.document;

import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.modules.document.dto.DocumentResponse;
import com.sogeco.fleet.modules.document.dto.DocumentUploadRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documents", description = "Pieces jointes et echeancier")
public class DocumentController {

    private final DocumentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Televerser un document")
    public DocumentResponse upload(@RequestPart("file") MultipartFile file,
                                   @Valid @RequestPart("metadata") DocumentUploadRequest metadata) {
        return service.upload(file, metadata);
    }

    @GetMapping
    @Operation(summary = "Lister les documents d'une entite")
    public List<DocumentResponse> listFor(@RequestParam EntityType entityType,
                                          @RequestParam Long entityId) {
        return service.listFor(entityType, entityId);
    }

    @GetMapping("/expiring")
    @Operation(summary = "Echeancier : documents arrivant a terme")
    public List<DocumentResponse> expiring(@RequestParam(defaultValue = "30") int days) {
        return service.expiringWithin(days);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Telecharger un document")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        Document document = service.find(id);
        byte[] content = service.download(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s\"".formatted(document.getFileName()))
                .contentType(MediaType.parseMediaType(
                        document.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                                                       : document.getMimeType()))
                .body(content);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiver un document")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
