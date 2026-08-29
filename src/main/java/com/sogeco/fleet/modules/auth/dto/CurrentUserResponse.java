package com.sogeco.fleet.modules.auth.dto;

import java.util.Set;

/** Profil de l'utilisateur connecte, consomme par le frontend au demarrage. */
public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        Long cityId,
        Set<String> roles,
        Set<String> permissions,
        boolean totpEnabled,
        boolean mustChangePassword
) {
}
