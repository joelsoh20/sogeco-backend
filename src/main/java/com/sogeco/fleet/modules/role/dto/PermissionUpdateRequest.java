package com.sogeco.fleet.modules.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** Remplace integralement les permissions d'un role. */
public record PermissionUpdateRequest(
        @NotNull(message = "la liste des permissions est obligatoire")
        Set<String> permissionCodes
) {
}
