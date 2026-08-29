package com.sogeco.fleet.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Jetons et profil de l'utilisateur connecte")
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        Long userId,
        String email,
        String fullName,
        Long cityId,
        Set<String> roles,
        Set<String> permissions,
        boolean mustChangePassword,
        boolean totpEnabled
) {
    public static final String BEARER = "Bearer";
}
