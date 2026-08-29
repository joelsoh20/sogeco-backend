package com.sogeco.fleet.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Point de controle de bon demarrage du squelette (sprint 0).
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Systeme", description = "Verification de disponibilite")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${sogeco.currency}")
    private String currency;

    @Value("${sogeco.timezone}")
    private String timezone;

    @GetMapping("/ping")
    @Operation(summary = "Verifier que l'application repond")
    public Map<String, Object> ping() {
        return Map.of(
                "application", applicationName,
                "status", "UP",
                "currency", currency,
                "timezone", timezone,
                "serverTime", Instant.now().toString()
        );
    }
}
