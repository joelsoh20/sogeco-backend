package com.sogeco.fleet.modules.role;

import com.sogeco.fleet.modules.role.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Roles et permissions", description = "Gestion des droits d'acces")
public class RoleController {

    private final RoleService service;

    @GetMapping("/roles")
    @Operation(summary = "Lister les roles avec leur nombre d'utilisateurs")
    public List<RoleResponse> list() {
        return service.list();
    }

    @GetMapping("/permissions")
    @Operation(summary = "Lister le referentiel de permissions, groupe par module")
    public List<PermissionResponse> listPermissions() {
        return service.listPermissions();
    }

    @PostMapping("/roles")
    @Operation(summary = "Creer un role")
    public RoleResponse create(@Valid @RequestBody RoleRequest request) {
        return service.create(request);
    }

    @PutMapping("/roles/{id}/permissions")
    @Operation(summary = "Remplacer les permissions d'un role")
    public RoleResponse updatePermissions(@PathVariable Long id,
                                          @Valid @RequestBody PermissionUpdateRequest request) {
        return service.updatePermissions(id, request);
    }

    @DeleteMapping("/roles/{id}")
    @Operation(summary = "Desactiver un role non systeme")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
