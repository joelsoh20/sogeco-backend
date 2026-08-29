package com.sogeco.fleet.modules.tracking;

import com.sogeco.fleet.common.enums.WebhookStatus;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.tracking.adapter.TelematicsPayloadAdapter;
import com.sogeco.fleet.modules.tracking.dto.TelematicsPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Point d'entree des trames telematiques.
 *
 * PRINCIPE DIRECTEUR : accuser reception en moins de 200 ms. La trame
 * est enregistree brute, la reponse part immediatement, et tout le
 * traitement metier se fait en asynchrone. Un traitement lent
 * provoquerait des rejeux cote fournisseur et une saturation en
 * cascade.
 *
 * Seul point d'entree non authentifie par JWT : la protection repose
 * sur un secret partage, une restriction reseau en production, et le
 * controle de vraisemblance des trames.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/telematics")
@Tag(name = "Webhook telematique", description = "Reception des positions GPS")
public class TelematicsWebhookController {

    private static final String SECRET_HEADER = "X-Webhook-Secret";

    private final Map<String, TelematicsPayloadAdapter> adapters = new HashMap<>();
    private final WebhookEventRepository webhookRepository;
    private final TelematicsIngestionService ingestionService;
    private final SettingService settingService;

    public TelematicsWebhookController(List<TelematicsPayloadAdapter> adapterList,
                                       WebhookEventRepository webhookRepository,
                                       TelematicsIngestionService ingestionService,
                                       SettingService settingService) {
        adapterList.forEach(adapter -> adapters.put(adapter.provider(), adapter));
        this.webhookRepository = webhookRepository;
        this.ingestionService = ingestionService;
        this.settingService = settingService;
        log.info("Webhook telematique : adaptateurs disponibles {}", adapters.keySet());
    }

    @PostMapping("/{provider}")
    @Operation(summary = "Recevoir une ou plusieurs positions")
    public ResponseEntity<Map<String, Object>> receive(@PathVariable String provider,
                                                       @RequestBody String rawBody,
                                                       HttpServletRequest request) {

        boolean authorized = checkSecret(request);

        // La trame est enregistree AVANT tout traitement, meme rejetee :
        // c'est ce qui permet de diagnostiquer une integration sans avoir
        // a reproduire le probleme.
        WebhookEvent event = persistRaw(provider, rawBody, authorized);

        if (!authorized) {
            log.warn("Trame refusee : secret invalide, provider={}", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "unauthorized"));
        }

        TelematicsPayloadAdapter adapter = adapters.get(provider.toLowerCase());
        if (adapter == null) {
            event.markRejected(WebhookStatus.REJETE, "Fournisseur inconnu : " + provider);
            webhookRepository.save(event);
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "unknown_provider", "provider", provider));
        }

        try {
            List<TelematicsPayload> payloads = adapter.parse(rawBody);

            if (!payloads.isEmpty()) {
                event.setDeviceId(payloads.get(0).deviceId());
                webhookRepository.save(event);
            }

            // Traitement asynchrone : la reponse part sans l'attendre.
            payloads.forEach(payload -> ingestionService.process(event.getId(), payload));

            return ResponseEntity.accepted()
                    .body(Map.of("status", "accepted", "count", payloads.size()));

        } catch (RuntimeException e) {
            event.markRejected(WebhookStatus.REJETE, e.getMessage());
            webhookRepository.save(event);
            log.warn("Trame illisible du fournisseur {} : {}", provider, e.getMessage());

            // On repond 202 malgre l'echec : un 4xx declencherait des
            // rejeux inutiles pour une trame qui restera illisible.
            return ResponseEntity.accepted()
                    .body(Map.of("status", "rejected", "reason", "unreadable"));
        }
    }

    /**
     * Enregistrement dans une transaction independante : la trace de la
     * trame doit subsister meme si le traitement echoue ensuite.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected WebhookEvent persistRaw(String provider, String rawBody, boolean authorized) {
        return webhookRepository.save(WebhookEvent.builder()
                .provider(provider)
                .payload(rawBody)
                .signatureValid(authorized)
                .status(WebhookStatus.RECU)
                .build());
    }

    /**
     * Comparaison a temps constant : une comparaison naive laisserait
     * fuir la longueur du prefixe correct par le temps de reponse.
     */
    private boolean checkSecret(HttpServletRequest request) {
        String expected = settingService.getString("telematics.webhook_secret", null);
        if (expected == null || expected.isBlank()) {
            log.warn("Aucun secret de webhook configure : toutes les trames sont acceptees");
            return true;
        }

        String provided = request.getHeader(SECRET_HEADER);
        if (provided == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.trim().getBytes(StandardCharsets.UTF_8));
    }
}
