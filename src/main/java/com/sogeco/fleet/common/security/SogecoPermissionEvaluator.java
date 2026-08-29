package com.sogeco.fleet.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Evaluateur de permissions pour hasPermission() dans @PreAuthorize.
 *
 * Deux usages :
 *   hasPermission('VEHICLE', 'CREATE')          -> droit sur un module
 *   hasPermission(#id, 'Driver', 'SELF_READ')   -> droit sur un objet precis
 *
 * Un administrateur passe toujours. Le role chauffeur, lui, est restreint
 * a ses propres donnees : la verification objet est appliquee par les
 * services concernes a partir du sprint 2.
 */
@Slf4j
@Component
public class SogecoPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        String required = buildCode(targetDomainObject, permission);
        return principal.hasPermission(required);
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId,
                                 String targetType, Object permission) {
        if (!(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            return false;
        }
        if (principal.isAdmin()) {
            return true;
        }
        return principal.hasPermission(String.valueOf(permission));
    }

    /** VEHICLE + CREATE -> VEHICLE_CREATE */
    private String buildCode(Object module, Object action) {
        if (module == null) {
            return String.valueOf(action).toUpperCase();
        }
        return (module + "_" + action).toUpperCase();
    }
}
