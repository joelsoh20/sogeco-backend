package com.sogeco.fleet.modules.auth;

import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.JwtService;
import com.sogeco.fleet.common.security.TotpService;
import com.sogeco.fleet.common.security.UserPrincipal;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.auth.dto.*;
import com.sogeco.fleet.modules.setting.SettingService;
import com.sogeco.fleet.modules.user.User;
import com.sogeco.fleet.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orchestration de l'authentification.
 *
 * Regles couvertes : RG-1.1 a RG-1.6 du cahier fonctionnel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ENTITY = "User";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TotpService totpService;
    private final SettingService settingService;
    private final AuditService auditService;

    // ------------------------------------------------------------------
    // Connexion
    // ------------------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        User user = resolveUser(request, ipAddress);

        if (!user.isActive()) {
            auditService.record(user.getEmail(), AuditAction.LOGIN_FAILURE, ENTITY, user.getId(), ipAddress);
            throw new BusinessException("RG-1.5", "Ce compte est suspendu", HttpStatus.FORBIDDEN);
        }

        if (user.isLocked()) {
            throw new BusinessException("RG-1.3",
                    "Compte temporairement verrouille apres plusieurs echecs. Reessayez plus tard.",
                    HttpStatus.LOCKED);
        }

        if (user.getPasswordHash() == null) {
            throw new BusinessException("RG-1.2",
                    "Ce compte utilise la connexion Google", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            registerFailure(user, ipAddress);
            throw invalidCredentials();
        }

        // Double authentification (RG-1.4)
        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new BusinessException("RG-1.4",
                        "Code de double authentification requis", HttpStatus.UNAUTHORIZED);
            }
            if (!totpService.verify(user.getTotpSecret(), request.totpCode())) {
                registerFailure(user, ipAddress);
                throw new BusinessException("RG-1.4",
                        "Code de double authentification invalide", HttpStatus.UNAUTHORIZED);
            }
        }

        user.registerSuccessfulLogin();
        auditService.record(user.getEmail(), AuditAction.LOGIN_SUCCESS, ENTITY, user.getId(), ipAddress);

        return buildResponse(user, ipAddress);
    }

    /**
     * Resout l'utilisateur par email, ou a defaut par nom + prenom.
     * Le nom/prenom n'etant pas unique, plusieurs correspondances sont
     * traitees exactement comme aucune — jamais un choix implicite du
     * premier resultat, qui connecterait potentiellement la mauvaise
     * personne.
     */
    private User resolveUser(LoginRequest request, String ipAddress) {
        boolean byEmail = request.email() != null && !request.email().isBlank();
        String identifierForAudit = byEmail
                ? request.email()
                : "%s %s".formatted(request.firstName(), request.lastName());

        if (byEmail) {
            return userRepository.findWithRolesByEmailIgnoreCase(request.email())
                    .orElseThrow(() -> {
                        // Message volontairement identique a celui d'un mot de passe
                        // errone : ne pas reveler quels comptes existent.
                        auditService.record(identifierForAudit, AuditAction.LOGIN_FAILURE, ENTITY, null, ipAddress);
                        return invalidCredentials();
                    });
        }

        List<User> matches = userRepository.findWithRolesByFirstNameIgnoreCaseAndLastNameIgnoreCase(
                request.firstName().trim(), request.lastName().trim());
        if (matches.size() != 1) {
            auditService.record(identifierForAudit, AuditAction.LOGIN_FAILURE, ENTITY, null, ipAddress);
            throw invalidCredentials();
        }
        return matches.get(0);
    }

    private void registerFailure(User user, String ipAddress) {
        int maxAttempts = settingService.getInt(SettingService.MAX_FAILED_ATTEMPTS, 5);
        int lockMinutes = settingService.getInt(SettingService.LOCK_DURATION_MINUTES, 15);

        user.registerFailedAttempt(maxAttempts, lockMinutes);
        auditService.record(user.getEmail(), AuditAction.LOGIN_FAILURE, ENTITY, user.getId(), ipAddress);

        if (user.isLocked()) {
            log.warn("Compte {} verrouille apres {} echecs", user.getEmail(), user.getFailedAttempts());
            auditService.record(user.getEmail(), AuditAction.ACCOUNT_LOCKED, ENTITY, user.getId(), ipAddress);
        }
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("RG-1.1", "Identifiants invalides", HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------
    // Rafraichissement et deconnexion
    // ------------------------------------------------------------------

    @Transactional
    public AuthResponse refresh(RefreshRequest request, String ipAddress) {
        User user = refreshTokenService.consume(request.refreshToken());

        User loaded = userRepository.findWithRolesById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", user.getId()));

        if (!loaded.isActive()) {
            throw new BusinessException("RG-1.5", "Ce compte est suspendu", HttpStatus.FORBIDDEN);
        }

        return buildResponse(loaded, ipAddress);
    }

    @Transactional
    public void logout(Long userId, String ipAddress) {
        refreshTokenService.revokeAll(userId);
        userRepository.findById(userId).ifPresent(user ->
                auditService.record(user.getEmail(), AuditAction.LOGOUT, ENTITY, userId, ipAddress));
    }

    // ------------------------------------------------------------------
    // Changement de mot de passe par son titulaire
    // ------------------------------------------------------------------

    /**
     * Tout titulaire de compte, y compris un administrateur, change ainsi
     * son propre mot de passe — a distinguer de UserService.resetPassword(),
     * reserve a un administrateur agissant sur le compte d'un tiers.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (user.getPasswordHash() == null) {
            throw new BusinessException("RG-1.2",
                    "Ce compte utilise la connexion Google", HttpStatus.BAD_REQUEST);
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("RG-1.1", "Mot de passe actuel incorrect", HttpStatus.UNAUTHORIZED);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        // Les sessions en cours ailleurs ne connaissent plus le mot de passe :
        // on les force a se reconnecter, comme pour une reinitialisation.
        refreshTokenService.revokeAll(userId);

        auditService.record(user.getEmail(), AuditAction.PASSWORD_CHANGED, ENTITY, userId, ipAddress);
        log.info("Mot de passe de {} modifie par son titulaire", user.getEmail());
    }

    // ------------------------------------------------------------------
    // Double authentification
    // ------------------------------------------------------------------

    @Transactional
    public TotpSetupResponse initTotp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("RG-1.4",
                    "La double authentification est deja active", HttpStatus.CONFLICT);
        }

        // Le secret est enregistre mais pas encore actif : il ne le devient
        // qu'apres verification d'un premier code, ce qui evite de bloquer
        // un utilisateur dont l'application n'aurait pas scanne le QR code.
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        user.setTotpEnabled(false);

        return new TotpSetupResponse(secret,
                totpService.buildOtpAuthUri(secret, user.getEmail(), "SOGECO"));
    }

    @Transactional
    public void confirmTotp(Long userId, TotpVerifyRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (user.getTotpSecret() == null) {
            throw new BusinessException("RG-1.4",
                    "Aucune activation en cours", HttpStatus.CONFLICT);
        }
        if (!totpService.verify(user.getTotpSecret(), request.code())) {
            throw new BusinessException("RG-1.4", "Code invalide", HttpStatus.UNAUTHORIZED);
        }

        user.setTotpEnabled(true);
        auditService.record(user.getEmail(), AuditAction.TOTP_ENABLED, ENTITY, userId, ipAddress);
    }

    @Transactional
    public void disableTotp(Long userId, TotpVerifyRequest request, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        if (!totpService.verify(user.getTotpSecret(), request.code())) {
            throw new BusinessException("RG-1.4", "Code invalide", HttpStatus.UNAUTHORIZED);
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        auditService.record(user.getEmail(), AuditAction.TOTP_DISABLED, ENTITY, userId, ipAddress);
    }

    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getCity() == null ? null : user.getCity().getId(),
                user.getRoleCodes(),
                user.getPermissionCodes(),
                Boolean.TRUE.equals(user.getTotpEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    private AuthResponse buildResponse(User user, String ipAddress) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user, ipAddress);

        return new AuthResponse(
                accessToken,
                refreshToken,
                AuthResponse.BEARER,
                jwtService.getAccessTokenMinutes() * 60L,
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                principal.getCityId(),
                principal.getRoleCodes(),
                principal.getPermissionCodes(),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                Boolean.TRUE.equals(user.getTotpEnabled()));
    }
}
