package com.sogeco.fleet.modules.push;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Push", description = "Notifications push navigateur / PWA")
public class PushController {

    private final PushNotificationService service;

    public record SubscribeRequest(@NotBlank String endpoint, @NotBlank String p256dh, @NotBlank String auth) {
    }

    @GetMapping("/public-key")
    @Operation(summary = "Cle publique VAPID, necessaire a l'abonnement du navigateur")
    public Map<String, Object> publicKey() {
        return Map.of("enabled", service.enabled(), "publicKey", service.publicKey());
    }

    @PostMapping("/subscriptions")
    @PreAuthorize("hasAuthority('ALERT_READ')")
    @Operation(summary = "Enregistrer l'appareil courant pour recevoir les alertes en push")
    public ResponseEntity<Void> subscribe(@Valid @RequestBody SubscribeRequest request, HttpServletRequest http) {
        service.subscribe(request.endpoint(), request.p256dh(), request.auth(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    @Operation(summary = "Retirer l'appareil courant")
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        service.unsubscribe(endpoint);
        return ResponseEntity.noContent().build();
    }
}
