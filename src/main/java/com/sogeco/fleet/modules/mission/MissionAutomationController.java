package com.sogeco.fleet.modules.mission;

import com.sogeco.fleet.modules.mission.dto.MissionAutomationRequest;
import com.sogeco.fleet.modules.mission.dto.MissionAutomationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mission-automations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Missions", description = "Livraisons quotidiennes recurrentes")
public class MissionAutomationController {

    private final MissionAutomationService service;

    @GetMapping
    @Operation(summary = "Lister les livraisons automatisees (actives et desactivees)")
    public List<MissionAutomationResponse> list() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Automatiser une livraison quotidienne")
    public MissionAutomationResponse create(@Valid @RequestBody MissionAutomationRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Arreter definitivement une livraison automatisee")
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }
}
