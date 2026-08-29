package com.sogeco.fleet.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Acces a l'utilisateur courant depuis la couche service. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public static Optional<Long> currentUserId() {
        return currentUser().map(UserPrincipal::getId);
    }

    public static String currentUserEmail() {
        return currentUser().map(UserPrincipal::getEmail).orElse("system");
    }

    /**
     * Ville geree, pour le filtrage transverse des camions/chauffeurs/missions.
     * Vide pour un administrateur : il voit l'ensemble du parc.
     */
    public static Optional<Long> currentCityId() {
        return currentUser()
                .filter(principal -> !principal.isAdmin())
                .map(UserPrincipal::getCityId);
    }

    public static boolean hasPermission(String code) {
        return currentUser().map(principal -> principal.hasPermission(code)).orElse(false);
    }

    public static boolean isAdmin() {
        return currentUser().map(UserPrincipal::isAdmin).orElse(false);
    }
}
