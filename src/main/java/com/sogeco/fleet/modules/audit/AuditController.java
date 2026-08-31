package com.sogeco.fleet.modules.audit;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Journal d'audit", description = "Traces des actions sensibles")
public class AuditController {

    private final AuditLogRepository repository;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Consulter le journal d'audit")
    public PageResponse<AuditLogResponse> list(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findByUserCityId(cityId, pageable))
                .orElseGet(() -> repository.findAll(pageable));
        return PageResponse.from(page, AuditLogResponse::from);
    }

    @GetMapping("/entity")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "Historique d'un enregistrement precis")
    public PageResponse<AuditLogResponse> byEntity(@RequestParam String entityType,
                                                   @RequestParam Long entityId,
                                                   @PageableDefault(size = 50) Pageable pageable) {
        var page = SecurityUtils.currentCityId()
                .map(cityId -> repository.findByEntityTypeAndEntityIdAndUserCityId(entityType, entityId, cityId, pageable))
                .orElseGet(() -> repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable));
        return PageResponse.from(page, AuditLogResponse::from);
    }
}
