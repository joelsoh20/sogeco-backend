package com.sogeco.fleet.modules.document;

import com.sogeco.fleet.common.enums.DocumentType;
import com.sogeco.fleet.common.enums.EntityType;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.document.dto.DocumentResponse;
import com.sogeco.fleet.modules.document.dto.DocumentUploadRequest;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    public static final String SETTING_WARNING_DAYS  = "alert.expiry_warning_days";
    public static final String SETTING_BLOCKING_TYPES = "document.blocking_types";

    private final DocumentRepository repository;
    private final FileStorageService storage;
    private final SettingService settingService;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------
    // Televersement et consultation
    // ------------------------------------------------------------------

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public DocumentResponse upload(MultipartFile file, DocumentUploadRequest request) {

        if (request.documentType().hasExpiry() && request.expiryDate() == null) {
            throw new BusinessException("RG-8.2",
                    "Une date d'expiration est obligatoire pour un document de type %s"
                            .formatted(request.documentType()),
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }

        String path = storage.store(file);

        Document document = Document.builder()
                .entityType(request.entityType())
                .entityId(request.entityId())
                .documentType(request.documentType())
                .fileName(file.getOriginalFilename())
                .filePath(path)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .referenceNumber(request.referenceNumber())
                .issueDate(request.issueDate())
                .expiryDate(request.expiryDate())
                .notes(request.notes())
                .uploadedBy(SecurityUtils.currentUserId().flatMap(userRepository::findById).orElse(null))
                .build();

        document.refreshStatus(warningDays());
        return DocumentResponse.from(repository.save(document));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<DocumentResponse> listFor(EntityType entityType, Long entityId) {
        return repository.findByEntityTypeAndEntityIdAndActiveTrueOrderByDocumentTypeAsc(entityType, entityId)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public Document find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public byte[] download(Long id) {
        return storage.read(find(id).getFilePath());
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void deactivate(Long id) {
        find(id).deactivate();
    }

    // ------------------------------------------------------------------
    // Echeancier
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<DocumentResponse> expiringWithin(int days) {
        return repository.findExpiringBefore(LocalDate.now().plusDays(days))
                .stream().map(DocumentResponse::from).toList();
    }

    /**
     * Recalcule le statut de tous les documents a echeance.
     * Appele chaque nuit par la tache planifiee (RG-8.2).
     */
    @Transactional
    public int refreshAllStatuses() {
        int warning = warningDays();
        List<Document> documents = repository.findByExpiryDateIsNotNullAndActiveTrue();
        documents.forEach(document -> document.refreshStatus(warning));
        log.info("Statuts d'echeance recalcules pour {} documents", documents.size());
        return documents.size();
    }

    // ------------------------------------------------------------------
    // Controle de conformite
    // ------------------------------------------------------------------

    /**
     * Documents bloquants expires pour un porteur donne.
     *
     * La liste des types bloquants est parametrable : par defaut
     * assurance, visite technique et carte grise (RG-4.5). Le
     * chronotachygraphe alerte sans bloquer.
     */
    @Transactional(readOnly = true)
    public List<Document> findBlockingExpired(EntityType entityType, Long entityId) {
        List<DocumentType> blocking = blockingTypes();
        if (blocking.isEmpty()) {
            return List.of();
        }
        return repository.findBlockingExpired(entityType, entityId, blocking);
    }

    private List<DocumentType> blockingTypes() {
        String raw = settingService.getString(SETTING_BLOCKING_TYPES,
                "ASSURANCE,VISITE_TECHNIQUE,CARTE_GRISE");
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return DocumentType.valueOf(value);
                    } catch (IllegalArgumentException e) {
                        log.warn("Type de document bloquant inconnu dans les parametres : {}", value);
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private int warningDays() {
        return settingService.getInt(SETTING_WARNING_DAYS, 30);
    }
}
