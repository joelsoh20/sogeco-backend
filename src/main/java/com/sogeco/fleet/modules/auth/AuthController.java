package com.sogeco.fleet.modules.auth;

import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.common.security.UserPrincipal;
import com.sogeco.fleet.modules.auth.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion, jetons et double authentification")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Se connecter")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return authService.login(request, clientIp(servletRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouveler le jeton d'acces")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
        return authService.refresh(request, clientIp(servletRequest));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Se deconnecter et revoquer les sessions")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal,
                                       HttpServletRequest servletRequest) {
        authService.logout(principal.getId(), clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Profil de l'utilisateur connecte")
    public CurrentUserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.currentUser(principal.getId());
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Changer son propre mot de passe",
            description = "Accessible a tout titulaire de compte, y compris un administrateur. "
                    + "Pour reinitialiser le mot de passe d'un tiers, voir POST /api/v1/users/{id}/reset-password.")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               HttpServletRequest servletRequest) {
        authService.changePassword(principal.getId(), request, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/setup")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Initier l'activation de la double authentification")
    public TotpSetupResponse setupTotp(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.initTotp(principal.getId());
    }

    @PostMapping("/2fa/confirm")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Confirmer l'activation par un premier code")
    public ResponseEntity<Void> confirmTotp(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody TotpVerifyRequest request,
                                            HttpServletRequest servletRequest) {
        authService.confirmTotp(principal.getId(), request, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/2fa/disable")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Desactiver la double authentification")
    public ResponseEntity<Void> disableTotp(@AuthenticationPrincipal UserPrincipal principal,
                                            @Valid @RequestBody TotpVerifyRequest request,
                                            HttpServletRequest servletRequest) {
        authService.disableTotp(principal.getId(), request, clientIp(servletRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * Adresse du client. En production l'application est derriere Nginx :
     * l'en-tete X-Forwarded-For porte alors l'adresse reelle.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
