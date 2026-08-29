package com.sogeco.fleet.modules.role.dto;

import com.sogeco.fleet.modules.role.Permission;
import com.sogeco.fleet.modules.role.Role;

import java.util.Comparator;
import java.util.List;

public record RoleResponse(
        Long id,
        String code,
        String label,
        String description,
        Boolean isSystem,
        Boolean active,
        long userCount,
        List<String> permissions
) {
    public static RoleResponse from(Role role, long userCount) {
        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getLabel(),
                role.getDescription(),
                role.getIsSystem(),
                role.getActive(),
                userCount,
                role.getPermissions().stream()
                        .map(Permission::getCode)
                        .sorted(Comparator.naturalOrder())
                        .toList());
    }
}
