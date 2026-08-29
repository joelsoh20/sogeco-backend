package com.sogeco.fleet.modules.document;

import com.sogeco.fleet.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Stockage des pieces jointes sur le systeme de fichiers.
 *
 * Les fichiers sont ranges par annee et par mois pour eviter un dossier
 * unique de plusieurs milliers d'entrees, et renommes en UUID : le nom
 * d'origine est conserve en base, jamais sur le disque. Cela neutralise
 * les traversees de chemin et les collisions de noms.
 */
@Slf4j
@Service
public class FileStorageService {

    private static final List<String> ALLOWED_TYPES = List.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    @Value("${sogeco.storage.path:./uploads}")
    private String storagePath;

    private Path root;

    @PostConstruct
    void init() {
        try {
            this.root = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.createDirectories(root);
            log.info("Stockage des documents : {}", root);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le dossier de stockage : " + storagePath, e);
        }
    }

    /** Enregistre le fichier et retourne son chemin relatif. */
    public String store(MultipartFile file) {
        validate(file);

        LocalDate today = LocalDate.now();
        String relativeDir = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String storedName = UUID.randomUUID() + extensionOf(file.getOriginalFilename());

        try {
            Path directory = root.resolve(relativeDir);
            Files.createDirectories(directory);
            Path target = directory.resolve(storedName);
            file.transferTo(target);
            return relativeDir + "/" + storedName;
        } catch (IOException e) {
            log.error("Echec d'ecriture du fichier {}", file.getOriginalFilename(), e);
            throw new BusinessException("STORAGE_ERROR",
                    "Impossible d'enregistrer le fichier", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] read(String relativePath) {
        try {
            Path target = resolveSafely(relativePath);
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new BusinessException("STORAGE_ERROR",
                    "Fichier introuvable", HttpStatus.NOT_FOUND);
        }
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolveSafely(relativePath));
        } catch (IOException e) {
            log.warn("Suppression du fichier {} impossible", relativePath, e);
        }
    }

    // ------------------------------------------------------------------

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("STORAGE_ERROR", "Fichier vide", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("STORAGE_ERROR",
                    "Fichier trop volumineux, 10 Mo maximum", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("STORAGE_ERROR",
                    "Format non accepte. Formats autorises : PDF, JPEG, PNG, WebP",
                    HttpStatus.UNPROCESSABLE_CONTENT);
        }
    }

    /** Empeche toute sortie du dossier de stockage (traversee de chemin). */
    private Path resolveSafely(String relativePath) {
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("STORAGE_ERROR", "Chemin invalide", HttpStatus.FORBIDDEN);
        }
        return target;
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot < 0 ? "" : originalName.substring(dot).toLowerCase(Locale.ROOT);
    }
}
