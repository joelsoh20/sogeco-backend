package com.sogeco.fleet.common.security;

import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.auth.RefreshTokenService;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Connexion Google (RG-1.1, RG-1.2).
 *
 * Le compte Google doit correspondre a un utilisateur DEJA cree dans
 * l'application : aucune creation automatique. Cela evite qu'une adresse
 * du domaine obtienne un acces sans decision d'un administrateur.
 *
 * Au succes, l'utilisateur est redirige vers le frontend avec les jetons
 * en parametres de fragment.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Value("${sogeco.oauth2.redirect-uri:http://localhost:5173/oauth2/callback}")
    private String redirectUri;

    @Value("${sogeco.oauth2.allowed-domain:}")
    private String allowedDomain;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");

        if (email == null) {
            redirectWithError(request, response, "email_absent");
            return;
        }

        if (!allowedDomain.isBlank() && !email.toLowerCase().endsWith("@" + allowedDomain.toLowerCase())) {
            log.warn("Connexion Google refusee, domaine non autorise : {}", email);
            redirectWithError(request, response, "domaine_non_autorise");
            return;
        }

        var found = userRepository.findWithRolesByEmailIgnoreCase(email);
        if (found.isEmpty()) {
            log.warn("Connexion Google refusee, aucun compte applicatif pour {}", email);
            redirectWithError(request, response, "compte_inexistant");
            return;
        }

        User user = found.get();
        if (!user.isActive()) {
            redirectWithError(request, response, "compte_suspendu");
            return;
        }

        // Rattachement du compte Google a la premiere connexion reussie.
        if (user.getGoogleId() == null) {
            user.setGoogleId(googleId);
        }
        user.registerSuccessfulLogin();

        String accessToken = jwtService.generateAccessToken(new UserPrincipal(user));
        String refreshToken = refreshTokenService.issue(user, request.getRemoteAddr());
        auditService.record(user.getEmail(), AuditAction.LOGIN_SUCCESS, "User", user.getId(), request.getRemoteAddr());

        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .fragment("accessToken=" + accessToken + "&refreshToken=" + refreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private void redirectWithError(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .fragment("error=" + reason)
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, target);
    }
}
