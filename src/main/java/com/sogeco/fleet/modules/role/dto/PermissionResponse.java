package com.sogeco.fleet.modules.role.dto;

import com.sogeco.fleet.modules.role.Permission;

public record PermissionResponse(Long id, String code, String module, String label) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(), permission.getCode(), permission.getModule(), permission.getLabel());
    }
}
