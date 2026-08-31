package com.sogeco.fleet.modules.user;

import com.sogeco.fleet.common.dto.PageResponse;
import com.sogeco.fleet.common.enums.UserStatus;
import com.sogeco.fleet.common.exception.BusinessException;
import com.sogeco.fleet.common.exception.DuplicateResourceException;
import com.sogeco.fleet.common.exception.ResourceNotFoundException;
import com.sogeco.fleet.common.security.SecurityUtils;
import com.sogeco.fleet.modules.audit.AuditAction;
import com.sogeco.fleet.modules.audit.AuditService;
import com.sogeco.fleet.modules.auth.RefreshTokenService;
import com.sogeco.fleet.modules.city.City;
import com.sogeco.fleet.modules.city.CityRepository;
import com.sogeco.fleet.modules.role.Role;
import com.sogeco.fleet.modules.role.RoleRepository;
import com.sogeco.fleet.modules.user.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String ENTITY = "User";
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository repository;
    private final RoleRepository roleRepository;
    private final CityRepository cityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(repository.findAllBy(pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse get(Long id) {
        return UserResponse.from(findWithRoles(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserCreationResult create(UserRequest request) {
        String email = request.email() == null || request.email().isBlank()
                ? placeholderEmail(request.firstName(), request.lastName())
                : request.email().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Utilisateur", "email", email);
        }

        // Un mot de passe non fourni est genere ici. Sans le retourner
        // a l'appelant, il serait perdu : ni l'administrateur ni
        // personne d'autre ne pourrait plus jamais s'y referer.
        boolean generated = request.password() == null || request.password().isBlank();
        String rawPassword = generated ? generatePassword() : request.password();

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .city(resolveCity(request.cityId()))
                .status(UserStatus.ACTIF)
                // Le mot de passe defini par l'administrateur — genere ou
                // saisi par lui — reste valable sans changement impose :
                // c'est l'administrateur qui gere les mots de passe, pas
                // le titulaire du compte.
                .mustChangePassword(false)
                .roles(resolveRoles(request.roleCodes()))
                .build();

        User saved = repository.save(user);
        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.USER_CREATED, ENTITY, saved.getId(), null);
        log.info("Utilisateur {} cree par {}", saved.getEmail(), SecurityUtils.currentUserEmail());

        return new UserCreationResult(UserResponse.from(saved), generated ? rawPassword : null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findWithRoles(id);

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        updateEmailIfProvided(user, request.email());
        user.setPhone(request.phone());
        user.setCity(resolveCity(request.cityId()));

        Set<Role> newRoles = resolveRoles(request.roleCodes());
        guardLastAdmin(user, newRoles);
        user.getRoles().clear();
        user.getRoles().addAll(newRoles);

        // Les droits ayant change, les jetons en cours ne sont plus a jour.
        refreshTokenService.revokeAll(id);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.USER_UPDATED, ENTITY, id, null);
        return UserResponse.from(user);
    }

    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserResponse changeStatus(Long id, UserStatus status) {
        User user = findWithRoles(id);
        assertCanDeactivate(id, user, status, "suspendre");

        if (status == UserStatus.ACTIF) {
            user.reactivate();
        } else {
            user.setStatus(status);
            refreshTokenService.revokeAll(id);
        }

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.USER_SUSPENDED, ENTITY, id, null);
        return UserResponse.from(user);
    }

    /**
     * Suppression par un administrateur — jamais physique (RG-13.2) :
     * l'historique (missions, sinistres... crees par ce compte) doit
     * rester consultable. Le compte devient inaccessible (statut
     * SUPPRIME, comme SUSPENDU au regard de la connexion) et disparait
     * des listes actives.
     */
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(Long id) {
        User user = findWithRoles(id);
        assertCanDeactivate(id, user, UserStatus.SUPPRIME, "supprimer");

        user.setStatus(UserStatus.SUPPRIME);
        user.setDeletedAt(Instant.now());
        refreshTokenService.revokeAll(id);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.USER_DELETED, ENTITY, id, null);
        log.info("Utilisateur {} supprime par {}", user.getEmail(), SecurityUtils.currentUserEmail());
    }

    /** Meme garde-fous pour la suspension et la suppression : jamais son propre compte, jamais le dernier admin actif. */
    private void assertCanDeactivate(Long id, User user, UserStatus targetStatus, String verb) {
        SecurityUtils.currentUserId().ifPresent(currentId -> {
            if (currentId.equals(id) && targetStatus != UserStatus.ACTIF) {
                throw new BusinessException("RG-13.2",
                        "Vous ne pouvez pas %s votre propre compte".formatted(verb), HttpStatus.CONFLICT);
            }
        });

        if (targetStatus != UserStatus.ACTIF && user.hasRole(Role.ADMIN) && countActiveAdmins() <= 1) {
            throw new BusinessException("RG-13.2",
                    "Impossible de %s le dernier administrateur actif".formatted(verb), HttpStatus.CONFLICT);
        }
    }

    /**
     * Reinitialisation par un administrateur.
     *
     * Le nouveau mot de passe est renvoye une seule fois et jamais
     * conserve en clair. Il reste valable sans changement impose, au
     * meme titre qu'a la creation : c'est l'administrateur qui gere les
     * mots de passe, pas le titulaire du compte.
     */
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PasswordResetResponse resetPassword(Long id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));

        String temporary = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(temporary));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        refreshTokenService.revokeAll(id);

        auditService.record(SecurityUtils.currentUserEmail(), AuditAction.PASSWORD_RESET, ENTITY, id, null);
        log.info("Mot de passe de {} reinitialise par {}", user.getEmail(), SecurityUtils.currentUserEmail());

        return new PasswordResetResponse(temporary);
    }

    /** Deverrouillage manuel apres echecs successifs. */
    @Transactional
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void unlock(Long id) {
        User user = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
    }

    // ------------------------------------------------------------------

    private void guardLastAdmin(User user, Set<Role> newRoles) {
        boolean wasAdmin = user.hasRole(Role.ADMIN);
        boolean staysAdmin = newRoles.stream().anyMatch(role -> Role.ADMIN.equals(role.getCode()));
        if (wasAdmin && !staysAdmin && countActiveAdmins() <= 1) {
            throw new BusinessException("RG-13.2",
                    "Impossible de retirer le role Administrateur au dernier administrateur",
                    HttpStatus.CONFLICT);
        }
    }

    private long countActiveAdmins() {
        return roleRepository.findByCode(Role.ADMIN)
                .map(repository::countByRolesContaining)
                .orElse(0L);
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        Set<Role> roles = new HashSet<>();
        for (String code : codes) {
            roles.add(roleRepository.findByCode(code)
                    .orElseThrow(() -> new BusinessException("RG-13.3",
                            "Role inconnu : " + code, HttpStatus.UNPROCESSABLE_CONTENT)));
        }
        return roles;
    }

    /**
     * Absente ou vide, l'adresse actuelle n'est pas touchee (reste
     * possiblement le placeholder genere a la creation). Fournie, elle
     * la remplace apres verification d'unicite — c'est ainsi qu'un
     * compte cree sans email reel peut en recevoir un par la suite.
     */
    private void updateEmailIfProvided(User user, String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.toLowerCase();
        if (!normalized.equals(user.getEmail()) && repository.existsByEmailIgnoreCase(normalized)) {
            throw new DuplicateResourceException("Utilisateur", "email", normalized);
        }
        user.setEmail(normalized);
    }

    private City resolveCity(Long cityId) {
        if (cityId == null) {
            return null;
        }
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ResourceNotFoundException("Ville", cityId));
    }

    /** 14 caracteres, sans les glyphes ambigus (O/0, l/1). */
    private String generatePassword() {
        StringBuilder builder = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    /**
     * Adresse interne, jamais communiquee : sans email reel, le compte
     * se connecte par nom et prenom (AuthService.resolveUser()), qui ne
     * regarde jamais cette valeur.
     */
    private String placeholderEmail(String firstName, String lastName) {
        String base = (firstName + "." + lastName).toLowerCase()
                .replaceAll("[^a-z0-9.]", "").replaceAll("\\.+", ".");
        if (base.isBlank()) {
            base = "utilisateur";
        }
        String candidate = base + "@sogeco.local";
        int suffix = 2;
        while (repository.existsByEmailIgnoreCase(candidate)) {
            candidate = base + suffix + "@sogeco.local";
            suffix++;
        }
        return candidate;
    }

    private User findWithRoles(Long id) {
        return repository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", id));
    }
}
